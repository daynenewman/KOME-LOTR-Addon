package com.enovak.lotrmoremobs.siege.ram;

import com.enovak.lotrmoremobs.config.MumakilConfig;
import com.enovak.lotrmoremobs.siege.access.GateAccess;
import com.enovak.lotrmoremobs.siege.gate.GateOrientation;
import com.enovak.lotrmoremobs.siege.gate.GatePartData;
import com.enovak.lotrmoremobs.siege.gate.GateState;
import com.enovak.lotrmoremobs.siege.network.SiegeNetwork;
import com.enovak.lotrmoremobs.siege.tile.TileEntitySiegeGate;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lotr.common.entity.npc.LOTREntityNPC;
import lotr.common.entity.npc.LOTRNPCMount;
import lotr.common.fac.LOTRFaction;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.IEntityLivingData;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAITasks;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.item.ItemStack;
import net.minecraft.util.*;
import net.minecraft.event.ClickEvent;
import net.minecraft.world.World;
import software.bernie.geckolib3.core.IAnimatable;
import software.bernie.geckolib3.core.manager.AnimationData;
import software.bernie.geckolib3.core.manager.AnimationFactory;
import net.minecraft.block.Block;
import net.minecraft.world.WorldServer;

public class EntityBattleRam extends net.minecraft.entity.EntityCreature
        implements IAnimatable {

    public static final double BASE_RAM_MOVE_SPEED = 0.22D;
    public static final double FOLLOW_START_DISTANCE = 5.0D;
    public static final double FOLLOW_STOP_DISTANCE = 3.0D;
    public static final double COMMANDER_TELEPORT_DISTANCE = 64.0D;
    private static final int COMMANDER_TELEPORT_SEARCH_RADIUS = 6;
    private static final int COMMANDER_TELEPORT_VERTICAL_SEARCH = 2;
    public static final int CREW_SLOT_COUNT = 10;
    public static final int CREW_RESPAWN_DELAY_TICKS = 600;
    public static final int RAM_SIEGE_DAMAGE = 100;
    public static final int ATTACK_INTERVAL_TICKS = 60;
    public static final int ATTACK_IMPACT_TICK = 30;

    /*
     * Physical attack-run tuning. The ram still uses ATTACK_GATE as its
     * externally visible state, but the strike itself is now a real server
     * movement cycle: back away from the aligned standoff point, charge back
     * to it, apply damage at contact, then recover briefly before repeating.
     */
    private static final double ATTACK_BACKUP_PREFERRED_DISTANCE = 4.0D;
    private static final double ATTACK_BACKUP_MIN_DISTANCE = 1.0D;
    /*
     * Charge strength is intentionally tiered rather than continuous. A full
     * retreat normally ends about 3.65-4.0 blocks from the gate because of
     * ATTACK_RETREAT_ARRIVAL_TOLERANCE, so 3.5 blocks cleanly represents the
     * authored full run while still tolerating normal movement rounding.
     */
    private static final double ATTACK_FULL_CHARGE_DISTANCE = 3.5D;
    private static final double ATTACK_MEDIUM_CHARGE_DISTANCE = 2.25D;
    private static final double ATTACK_MEDIUM_DAMAGE_MULTIPLIER = 0.70D;
    private static final double ATTACK_SHORT_DAMAGE_MULTIPLIER = 0.40D;
    private static final double ATTACK_RETREAT_ARRIVAL_TOLERANCE = 0.35D;
    private static final double ATTACK_CHARGE_ARRIVAL_TOLERANCE = 0.06D;
    private static final double ATTACK_CENTERLINE_TOLERANCE = 0.55D;

    /*
     * Gate attack movement bypasses PathNavigate for the final alignment and
     * physical retreat/charge. Give only that narrow movement path enough
     * step height to traverse ordinary one-block uphill terrain. Two-block
     * walls and normal collision remain blocking obstacles.
     */
    private static final float ATTACK_TERRAIN_STEP_HEIGHT = 1.0F;

    /*
     * The authored ram extends about three blocks forward from the entity
     * origin.  Attack positioning is therefore measured to the actual head
     * of the rendered ram, not to the entity center.  A tiny clearance keeps
     * the tip from z-fighting with the gate face at rest.
     */
    public static final double RAM_TIP_REACH_DISTANCE = 3.0D;
    private static final double RAM_TIP_CONTACT_CLEARANCE = 0.025D;

    private static final double ATTACK_BACKUP_SPEED_FACTOR = 0.72D;
    private static final double ATTACK_CHARGE_SPEED_FACTOR = 1.22D;
    private static final double ATTACK_RETREAT_START_SPEED_FACTOR = 0.38D;
    private static final double ATTACK_RETREAT_END_SPEED_FACTOR = 0.42D;
    private static final double ATTACK_CHARGE_START_SPEED_FACTOR = 0.42D;
    private static final int ATTACK_READY_TICKS = 10;
    private static final int ATTACK_REAR_BRACE_TICKS = 12;
    private static final int ATTACK_RECOVERY_TICKS = 12;
    private static final int ATTACK_BLOCKED_TICKS_BEFORE_SHORT_CHARGE = 8;
    private static final int ATTACK_PHASE_FAILURE_TICKS = 100;

    private static final double ATTACK_CREW_GROUND_SEARCH_DOWN = 1.35D;
    private static final double ATTACK_CREW_GROUND_SEARCH_UP = 0.85D;
    private static final double ATTACK_CREW_MAX_GROUND_DROP = 0.80D;
    private static final double ATTACK_CREW_MAX_GROUND_RISE = 0.65D;
    private static final double ATTACK_CREW_GROUND_SAMPLE_HALF_WIDTH = 0.20D;

    /*
     * Legacy authored-animation constants are retained for compatibility with
     * older callers/resources, but the runtime attack no longer uses a visual
     * lunge to simulate movement.
     */
    /*
     * Client presentation distances for the carrier-assisted ram strike.
     * These do not move the authoritative ram or crew hitboxes. The crew
     * render path uses the same authoritative attack phase and moves the
     * visible carriers only a small fraction of a block so the authored ram
     * strike reads as a coordinated shove rather than a beam moving by itself.
     */
    private static final float CREW_ATTACK_BRACE_DISTANCE = 0.04F;
    private static final float CREW_ATTACK_DRIVE_DISTANCE = 0.18F;
    private static final float CREW_ATTACK_BRACE_END_TICK = 8.0F;
    private static final float CREW_ATTACK_BRACE_RECOVER_END_TICK = 16.0F;
    private static final float CREW_ATTACK_DRIVE_START_TICK = 16.0F;
    private static final float CREW_ATTACK_IMPACT_RECOVER_END_TICK = 38.0F;
    private static final float CREW_ATTACK_LATE_RECOVER_DISTANCE = 0.08F;
    public static final int PATH_RETRY_INTERVAL_TICKS = 20;
    private static final int FAILED_PATH_RETRY_INTERVAL_TICKS = 5;
    private static final int MAX_CONSECUTIVE_PATH_REQUEST_FAILURES = 2;
    public static final int PATH_PROGRESS_CHECK_TICKS = 20;
    public static final int OPPOSITE_SIDE_RETRY_TICKS = 40;
    public static final int TARGET_FAILURE_TIMEOUT_TICKS = 200;
    public static final double ATTACK_STANDOFF_DISTANCE =
            RAM_TIP_REACH_DISTANCE + RAM_TIP_CONTACT_CLEARANCE;
    public static final double ATTACK_POSITION_HORIZONTAL_TOLERANCE = 2.25D;
    public static final double ATTACK_POSITION_VERTICAL_TOLERANCE = 1.5D;
    public static final double ATTACK_POSITION_EXIT_HORIZONTAL_TOLERANCE =
            3.0D;
    public static final double ATTACK_POSITION_EXIT_VERTICAL_TOLERANCE =
            2.0D;

    /*
     * Once ordinary pathing gets the ram close to the gate, finish the last
     * small amount of movement ourselves. This guarantees the ram reaches the
     * exact gate midline before the attack animation can begin instead of
     * accepting Minecraft's much looser "close enough" path endpoint.
     */
    public static final double ATTACK_ALIGNMENT_START_DISTANCE = 1.25D;
    public static final double ATTACK_ALIGNMENT_POSITION_TOLERANCE = 0.08D;
    public static final double ATTACK_ALIGNMENT_STEP = 0.10D;
    private static final double ATTACK_ALIGNMENT_MIN_PROGRESS = 0.01D;
    private static final int ATTACK_ALIGNMENT_FAILURE_TICKS = 15;
    public static final double PATH_PROGRESS_EPSILON = 0.25D;
    private static final double PATH_TRAVEL_EPSILON = 0.35D;

    /*
     * Adjacent gate columns are usually the same physical approach for the
     * ram. After one lane genuinely fails, require the next candidate to be
     * at least two gate columns away from every failed lane. This makes an
     * alternate attempt a meaningfully different corridor instead of burning
     * through neighboring columns until the target is abandoned.
     */
    private static final int ATTACK_ALTERNATE_LANE_MIN_SEPARATION = 2;
    private static final int ATTACK_MAX_DISTINCT_LANES_PER_FACE = 3;
    private static final int NO_ATTACK_LANE = Integer.MIN_VALUE;
    public static final int MAX_TARGET_QUEUE_SIZE = 64;

    public static final int CREW_REASSOCIATION_GRACE_TICKS = 100;
    private static final int CREW_REASSOCIATION_INTERVAL_TICKS = 20;
    private static final double CREW_REASSOCIATION_RANGE = 16.0D;
    private static final String CREW_RAM_UUID = "SiegeRamUUID";
    private static final String CREW_SLOT = "SiegeRamCrewSlot";
    private static final String CREW_GENERATION = "SiegeRamGeneration";
    private static final String CREW_MARKER = "SiegeRamCarrier";
    private static final String CREW_HELD_ITEM_CAPTURED =
            "SiegeRamHeldItemCaptured";
    private static final String CREW_HELD_ITEM = "SiegeRamHeldItem";
    private static final String CREW_HELD_ITEM_CAPTURE_VERSION =
            "SiegeRamHeldItemCaptureVersion";
    private static final int CURRENT_HELD_ITEM_CAPTURE_VERSION = 2;
    private static final String CREW_COLLISION_REDUCTION_CAPTURED =
            "SiegeRamCollisionReductionCaptured";
    private static final String CREW_COLLISION_REDUCTION =
            "SiegeRamCollisionReduction";
    private static final Field NPC_CURRENT_ATTACK_MODE_FIELD =
            getNpcAttackModeField("currentAttackMode");
    private static final Field NPC_FIRST_UPDATED_ATTACK_MODE_FIELD =
            getNpcAttackModeField("firstUpdatedAttackMode");
    private static final Object NPC_IDLE_ATTACK_MODE = getNpcIdleAttackMode();

    private static final int WATCHER_STATE = 20;
    private static final int WATCHER_FACTION = 21;
    private static final int WATCHER_HAS_TARGET = 22;
    private static final int WATCHER_TARGET_X = 23;
    private static final int WATCHER_TARGET_Y = 24;
    private static final int WATCHER_TARGET_Z = 25;
    private static final int WATCHER_LIVING_CREW = 26;
    private static final int WATCHER_ATTACK_CYCLE_START = 27;
    private static final int WATCHER_ATTACK_PHASE = 28;
    private static final int WATCHER_IMPACT_SERIAL = 29;
    private static final int NO_ATTACK_CYCLE_START = Integer.MIN_VALUE;
    private static final String NBT_COMMANDER_UUID = "CommanderUUID";
    private static final String NBT_FACTION = "RamFaction";
    private static final String NBT_STATE = "RamState";
    private static final String NBT_RESUME_STATE = "RamResumeState";
    private static final String NBT_CREW_CONFIGURED = "CrewConfigured";
    private static final String NBT_CREW_SLOTS = "CrewSlots";
    private static final String NBT_CREW_SLOT = "Slot";
    private static final String NBT_CREW_ALIVE = "Alive";
    private static final String NBT_CREW_UUID = "CrewUUID";
    private static final String NBT_CREW_RESPAWN_AT = "RespawnAt";
    private static final String NBT_TARGET_DIMENSION = "TargetDimension";
    private static final String NBT_TARGET_X = "TargetX";
    private static final String NBT_TARGET_Y = "TargetY";
    private static final String NBT_TARGET_Z = "TargetZ";
    private static final String NBT_TARGET_GATE_UUID = "TargetGateUUID";
    private static final String NBT_TARGET_QUEUE = "TargetQueue";
    private static final String NBT_QUEUE_DIMENSION = "Dimension";
    private static final String NBT_QUEUE_X = "X";
    private static final String NBT_QUEUE_Y = "Y";
    private static final String NBT_QUEUE_Z = "Z";
    private static final String NBT_QUEUE_GATE_UUID = "GateUUID";
    private static final String NBT_ATTACK_TICKS = "AttackTicks";
    private static final String NBT_ATTACK_PROGRESS_REMAINDER =
            "AttackProgressRemainder";

    private static final double[][] CREW_OFFSETS = {
            {-0.75D, -2.7D}, {0.75D, -2.7D},
            {-0.75D, -1.7D}, {0.75D, -1.7D},
            {-0.75D, -0.8D}, {0.75D, -0.8D},
            {-0.75D, 0.3D}, {0.75D, 0.3D},
            {-0.75D, 1.3D}, {0.75D, 1.3D}
    };

    private UUID commanderUuid;
    private long ramGeneration;
    private boolean durableOwnershipReady;
    private boolean worldUnloading;
    private BattleRamState resumeState = BattleRamState.FOLLOW_COMMANDER;
    private boolean deliberateDisband;
    private boolean crewConfigured;
    private final boolean[] crewSlotAlive =
            new boolean[CREW_SLOT_COUNT];
    private final UUID[] crewEntityUuids = new UUID[CREW_SLOT_COUNT];
    private final long[] crewRespawnAt = new long[CREW_SLOT_COUNT];
    private final LOTREntityNPC[] crewReferences =
            new LOTREntityNPC[CREW_SLOT_COUNT];
    private int targetDimension;
    private UUID targetGateUuid;
    private final List<QueuedGateTarget> targetQueue =
            new ArrayList<QueuedGateTarget>();
    private int attackTicks;
    private float attackProgressRemainder;

    private PhysicalAttackPhase physicalAttackPhase =
            PhysicalAttackPhase.NONE;
    private double attackRetreatX;
    private double attackRetreatZ;
    private double attackRetreatDistance;
    private double attackChargeStartDistance;
    private int attackPhysicalPhaseTicks;
    private int attackPhysicalBlockedTicks;

    private int pathFailureTicks;
    private int pathProgressCheckTicks;
    private int pathRequestCooldownTicks;
    private double lastPathDistance = Double.MAX_VALUE;
    private double lastPathProgressX = Double.NaN;
    private double lastPathProgressZ = Double.NaN;
    private boolean lastPathRequestSucceeded;
    private boolean pathRequestFailedSinceProgressCheck;
    private int consecutivePathRequestFailures;
    private boolean triedOppositeSide;
    private int attackSideSign;
    private int attackLaneCoordinate = NO_ATTACK_LANE;
    private final List<Integer> failedAttackLaneCoordinates =
            new ArrayList<Integer>();
    private int attackAlignmentFailureTicks;
    private final AnimationFactory animationFactory =
            new AnimationFactory(this);
    private boolean hirePreview;

    public EntityBattleRam(World world) {
        super(world);
        setSize(1.0F, 1.5F);
        entityCollisionReduction = 1.0F;
        isImmuneToFire = true;
        experienceValue = 0;
        getNavigator().setAvoidsWater(true);
    }

    @Override
    protected void entityInit() {
        super.entityInit();
        dataWatcher.addObject(
                WATCHER_STATE,
                Integer.valueOf(BattleRamState.FOLLOW_COMMANDER.ordinal())
        );
        dataWatcher.addObject(WATCHER_FACTION, "");
        dataWatcher.addObject(WATCHER_HAS_TARGET, Byte.valueOf((byte)0));
        dataWatcher.addObject(WATCHER_TARGET_X, Integer.valueOf(0));
        dataWatcher.addObject(WATCHER_TARGET_Y, Integer.valueOf(0));
        dataWatcher.addObject(WATCHER_TARGET_Z, Integer.valueOf(0));
        dataWatcher.addObject(WATCHER_LIVING_CREW, Integer.valueOf(0));
        dataWatcher.addObject(
                WATCHER_ATTACK_CYCLE_START,
                Integer.valueOf(NO_ATTACK_CYCLE_START)
        );
        dataWatcher.addObject(
                WATCHER_ATTACK_PHASE,
                Float.valueOf(0.0F)
        );
        dataWatcher.addObject(
                WATCHER_IMPACT_SERIAL,
                Integer.valueOf(0)
        );
    }

    public void registerControllers(AnimationData data) {
        // Presentation keyframes are evaluated by the safe client renderer.
    }

    public AnimationFactory getFactory() {
        return animationFactory;
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        getEntityAttribute(SharedMonsterAttributes.maxHealth)
                .setBaseValue(20.0D);
        getEntityAttribute(SharedMonsterAttributes.movementSpeed)
                .setBaseValue(BASE_RAM_MOVE_SPEED);
        getEntityAttribute(SharedMonsterAttributes.followRange)
                .setBaseValue(64.0D);
    }

    @Override
    protected boolean isAIEnabled() {
        // EntityLiving's 1.7.10 default is the legacy AI loop, which never
        // advances PathNavigate paths created by the ram's movement states.
        return true;
    }

    public boolean initializeForCommander(
            EntityPlayer player,
            LOTRFaction faction
    ) {
        if (player == null || worldObj == null || worldObj.isRemote) {
            return false;
        }
        commanderUuid = player.getUniqueID();
        setRamFaction(faction);
        setRamState(BattleRamState.FOLLOW_COMMANDER);
        initializeCrewSlots();
        SiegeRamCrewOwnershipData data = SiegeRamCrewOwnershipData.get(
                worldObj, true
        );
        SiegeRamCrewOwnershipData.RamSnapshot registered = data == null
                ? null : data.registerNewRam(worldObj, getUniqueID(), faction);
        if (registered == null) {
            crewConfigured = false;
            return false;
        }
        ramGeneration = registered.generation;
        durableOwnershipReady = true;
        data.touchRam(
                worldObj, getUniqueID(), ramGeneration,
                ((int)Math.floor(posX)) >> 4, ((int)Math.floor(posZ)) >> 4
        );
        return true;
    }

    public void cancelUnspawnedHireInitialization() {
        if (worldObj == null || worldObj.isRemote) {
            return;
        }
        if (durableOwnershipReady && ramGeneration > 0L) {
            SiegeRamCrewOwnershipData data = SiegeRamCrewOwnershipData.get(
                    worldObj,
                    false
            );
            if (data != null) {
                data.retireRam(
                        worldObj,
                        getUniqueID(),
                        ramGeneration,
                        SiegeRamCrewOwnershipData.RamStatus
                                .DISBANDED_TOMBSTONE
                );
            }
        }
        durableOwnershipReady = false;
        crewConfigured = false;
        deliberateDisband = true;
        setDead();
    }

    @Override
    public void onLivingUpdate() {
        super.onLivingUpdate();
        synchronizeRamFormationHeading();
        if (!MumakilConfig.enableBattleRams) {
            if (!worldObj.isRemote) {
                getNavigator().clearPathEntity();
            }
            motionX = 0.0D;
            motionZ = 0.0D;
            return;
        }
        if (worldObj.isRemote || deliberateDisband) {
            return;
        }
        if (!ensureDurableOwnership()) {
            getNavigator().clearPathEntity();
            return;
        }
        updateCrewSlots();
        refreshAssignedGateReservation();
        if (getRamState() == BattleRamState.PAUSED) {
            getNavigator().clearPathEntity();
            motionX = 0.0D;
            motionZ = 0.0D;
        } else if (getRamState() == BattleRamState.FOLLOW_COMMANDER) {
            updateCommanderFollow();
        } else if (getRamState() == BattleRamState.MOVE_TO_GATE) {
            updateMoveToGate();
        } else if (getRamState() == BattleRamState.ATTACK_GATE) {
            updateAttackGate();
        }
    }

    private void updateCommanderFollow() {
        EntityPlayer commander = getCommander();
        if (commander == null || commander.isDead) {
            getNavigator().clearPathEntity();
            return;
        }
        double speedMultiplier = getCrewSpeedMultiplier();
        if (speedMultiplier <= 0.0D) {
            getNavigator().clearPathEntity();
            return;
        }
        double distanceSq = getDistanceSqToEntity(commander);

        /*
         * Mirror the practical behavior of LOTR hired-unit following: when
         * the commander moves a very large distance at once (especially LOTR
         * fast travel), bring the complete ram formation back near them rather
         * than asking PathNavigate to cross the entire region. Before the jump
         * is committed, the bounded ten-slot recovery pass makes sure the ram
         * cannot leave a living carrier behind in an adjacent unloaded chunk.
         */
        if (distanceSq
                > COMMANDER_TELEPORT_DISTANCE
                * COMMANDER_TELEPORT_DISTANCE
                && prepareCrewForFormationTeleport()
                && tryTeleportNearCommander(commander)) {
            return;
        }

        if (distanceSq > FOLLOW_START_DISTANCE * FOLLOW_START_DISTANCE) {
            if (pathRequestCooldownTicks > 0) {
                --pathRequestCooldownTicks;
            }
            if (pathRequestCooldownTicks <= 0) {
                lastPathRequestSucceeded =
                        getNavigator().tryMoveToEntityLiving(
                                commander,
                                speedMultiplier
                        );
                pathRequestCooldownTicks = PATH_RETRY_INTERVAL_TICKS;
            }
        } else if (distanceSq
                < FOLLOW_STOP_DISTANCE * FOLLOW_STOP_DISTANCE) {
            getNavigator().clearPathEntity();
            pathRequestCooldownTicks = 0;
            lastPathRequestSucceeded = false;
        }
    }

    /**
     * Explicit LOTR fast-travel hook used by RamControlEventHandler.
     *
     * Only an owned ram that is genuinely idle-following the same commander
     * may travel this way. Gate-bound, queued, attacking, or paused rams stay
     * where they are.
     */
    public boolean teleportFollowFormationAfterFastTravel(
            EntityPlayer commander
    ) {
        if (commander == null
                || commander.isDead
                || worldObj == null
                || worldObj.isRemote
                || commander.worldObj != worldObj
                || commanderUuid == null
                || !commanderUuid.equals(commander.getUniqueID())
                || getRamState() != BattleRamState.FOLLOW_COMMANDER
                || hasGateTarget()
                || !targetQueue.isEmpty()
                || deliberateDisband
                || isDead
                || !ensureDurableOwnership()) {
            return false;
        }

        /*
         * Resolve every durable living carrier before the ram moves. A ram
         * formation can straddle chunk borders; after LOTR fast travel those
         * old carrier chunks may already have unloaded even though the ram
         * entity itself is still available. The durable slot record stores
         * each carrier's exact UUID and last chunk, so synchronously load only
         * those bounded origin chunks and rebuild the in-memory references.
         * If any living expected carrier still cannot be proven present, do
         * not split the formation by teleporting the ram alone.
         */
        if (!prepareCrewForFormationTeleport()) {
            return false;
        }
        return tryTeleportNearCommander(commander);
    }

    /**
     * Makes the ten-slot formation complete in memory before any long-range
     * relocation. This is intentionally bounded to the ram's durable slots;
     * it never installs a Forge chunk ticket or permanently force-loads an
     * origin area. Chunks pulled in here are ordinary server-loaded chunks and
     * may unload normally once the formation has moved away.
     */
    private boolean prepareCrewForFormationTeleport() {
        if (worldObj == null
                || worldObj.isRemote
                || isDead
                || deliberateDisband
                || !crewConfigured
                || !hasDurableOwnership()) {
            return false;
        }

        SiegeRamCrewOwnershipData data =
                SiegeRamCrewOwnershipData.get(worldObj, false);
        if (data == null) {
            return false;
        }

        synchronizeCrewSlotsFromDurableData();

        /*
         * Existing saves may have an older lastCrewChunk value because the
         * original implementation only refreshed that coordinate on attach.
         * A carrier formation is only a few blocks wider than the ram, so load
         * the ram's current 3x3 chunk neighborhood once before consulting the
         * durable per-slot fallback. This repairs those older saves without
         * broad or permanent chunk loading.
         */
        if (addedToChunk) {
            int ramChunkX = ((int)Math.floor(posX)) >> 4;
            int ramChunkZ = ((int)Math.floor(posZ)) >> 4;
            for (int chunkOffsetX = -1; chunkOffsetX <= 1; ++chunkOffsetX) {
                for (int chunkOffsetZ = -1; chunkOffsetZ <= 1; ++chunkOffsetZ) {
                    worldObj.getChunkFromChunkCoords(
                            ramChunkX + chunkOffsetX,
                            ramChunkZ + chunkOffsetZ
                    );
                }
            }
        }

        reassociateLoadedCrew();

        /*
         * A prepared respawn candidate is also an exact durable identity. If
         * one exists, load its prepared chunk and let the normal ownership
         * reconciler either promote that candidate or safely return the slot
         * to its bounded retry state before deciding what must travel.
         */
        for (int slot = 0; slot < CREW_SLOT_COUNT; ++slot) {
            SiegeRamCrewOwnershipData.SlotSnapshot value = data.getSlot(
                    getUniqueID(),
                    ramGeneration,
                    slot
            );
            if (value == null
                    || value.state
                    != SiegeRamCrewOwnershipData.SlotState.SPAWN_PREPARED) {
                continue;
            }

            worldObj.getChunkFromChunkCoords(
                    value.preparedChunkX,
                    value.preparedChunkZ
            );
            reconcilePreparedSpawnSlot(slot);
        }

        synchronizeCrewSlotsFromDurableData();

        for (int slot = 0; slot < CREW_SLOT_COUNT; ++slot) {
            SiegeRamCrewOwnershipData.SlotSnapshot value = data.getSlot(
                    getUniqueID(),
                    ramGeneration,
                    slot
            );

            if (value == null
                    || value.state
                    != SiegeRamCrewOwnershipData.SlotState.ALIVE_EXPECTED) {
                continue;
            }

            LOTREntityNPC current = crewReferences[slot];
            if (current != null
                    && getValidAttachedCrewSlot(current) == slot) {
                noteCurrentCarrierChunk(data, slot, current);
                continue;
            }
            crewReferences[slot] = null;

            LOTREntityNPC recovered = findLoadedExpectedCrew(
                    value.expectedCrewUuid
            );

            if (recovered == null && value.hasLastCrewChunk) {
                /*
                 * getChunkFromChunkCoords synchronously loads the exact saved
                 * carrier chunk when necessary. EntityJoinWorld reconciliation
                 * may attach it immediately; the explicit UUID scan below also
                 * covers event ordering differences.
                 */
                worldObj.getChunkFromChunkCoords(
                        value.lastCrewChunkX,
                        value.lastCrewChunkZ
                );
                recovered = findExpectedCrewInChunk(
                        value.expectedCrewUuid,
                        value.lastCrewChunkX,
                        value.lastCrewChunkZ
                );
                if (recovered == null) {
                    recovered = findLoadedExpectedCrew(
                            value.expectedCrewUuid
                    );
                }
            }

            if (recovered == null
                    || !attachLoadedCrewIfValid(recovered)
                    || getValidAttachedCrewSlot(recovered) != slot) {
                SiegeRamDiagnostics.serverOnce(
                        worldObj.provider.dimensionId,
                        "formation-teleport-unresolved:" + getUniqueID()
                                + ":" + ramGeneration + ":" + slot + ":"
                                + value.expectedCrewUuid,
                        "FORMATION_TELEPORT_UNRESOLVED",
                        "ram=" + getUniqueID() + " gen=" + ramGeneration
                                + " slot=" + slot + " expected="
                                + value.expectedCrewUuid + " hasLastChunk="
                                + value.hasLastCrewChunk + " lastChunk="
                                + value.lastCrewChunkX + ","
                                + value.lastCrewChunkZ
                );
                return false;
            }

            noteCurrentCarrierChunk(data, slot, recovered);
        }

        return true;
    }

    private LOTREntityNPC findLoadedExpectedCrew(UUID expectedUuid) {
        if (expectedUuid == null || worldObj == null) {
            return null;
        }
        for (Object object : worldObj.loadedEntityList) {
            if (!(object instanceof LOTREntityNPC)) {
                continue;
            }
            LOTREntityNPC candidate = (LOTREntityNPC)object;
            if (!candidate.isDead
                    && expectedUuid.equals(candidate.getUniqueID())) {
                return candidate;
            }
        }
        return null;
    }

    private LOTREntityNPC findExpectedCrewInChunk(
            UUID expectedUuid,
            int chunkX,
            int chunkZ
    ) {
        if (expectedUuid == null || worldObj == null) {
            return null;
        }
        AxisAlignedBB bounds = AxisAlignedBB.getBoundingBox(
                chunkX * 16,
                0.0D,
                chunkZ * 16,
                chunkX * 16 + 16,
                256.0D,
                chunkZ * 16 + 16
        );
        List candidates = worldObj.getEntitiesWithinAABB(
                LOTREntityNPC.class,
                bounds
        );
        for (Object object : candidates) {
            LOTREntityNPC candidate = (LOTREntityNPC)object;
            if (!candidate.isDead
                    && expectedUuid.equals(candidate.getUniqueID())) {
                return candidate;
            }
        }
        return null;
    }

    private void noteCurrentCarrierChunk(
            SiegeRamCrewOwnershipData data,
            int slot,
            LOTREntityNPC crew
    ) {
        if (data == null || crew == null) {
            return;
        }
        data.noteCarrierChunk(
                worldObj,
                getUniqueID(),
                ramGeneration,
                slot,
                crew.getUniqueID(),
                ((int)Math.floor(crew.posX)) >> 4,
                ((int)Math.floor(crew.posZ)) >> 4
        );
    }

    private boolean tryTeleportNearCommander(
            EntityPlayer commander
    ) {
        if (commander == null
                || commander.isDead
                || commander.worldObj != worldObj) {
            return false;
        }

        int baseX = MathHelper.floor_double(commander.posX);
        int baseY = MathHelper.floor_double(
                commander.boundingBox.minY
        );
        int baseZ = MathHelper.floor_double(commander.posZ);

        /*
         * Prefer a footprint large enough for the whole ten-carrier formation.
         * If the player fast-travelled into a tighter settlement, fall back to
         * ram-only clearance; carrier in-wall damage is separately suppressed
         * and the normal formation reconciler will settle them around the ram.
         */
        if (tryTeleportNearCommander(
                baseX,
                baseY,
                baseZ,
                true
        )) {
            return true;
        }

        return tryTeleportNearCommander(
                baseX,
                baseY,
                baseZ,
                false
        );
    }

    private boolean tryTeleportNearCommander(
            int baseX,
            int baseY,
            int baseZ,
            boolean requireFormationClearance
    ) {
        for (int radius = 2;
             radius <= COMMANDER_TELEPORT_SEARCH_RADIUS;
             ++radius) {

            for (int offsetX = -radius;
                 offsetX <= radius;
                 ++offsetX) {

                for (int offsetZ = -radius;
                     offsetZ <= radius;
                     ++offsetZ) {

                    if (Math.abs(offsetX) != radius
                            && Math.abs(offsetZ) != radius) {
                        continue;
                    }

                    for (int offsetY = COMMANDER_TELEPORT_VERTICAL_SEARCH;
                         offsetY >= -COMMANDER_TELEPORT_VERTICAL_SEARCH;
                         --offsetY) {

                        int blockX = baseX + offsetX;
                        int feetY = baseY + offsetY;
                        int blockZ = baseZ + offsetZ;

                        if (!worldObj.blockExists(
                                blockX,
                                feetY,
                                blockZ
                        )) {
                            continue;
                        }

                        Block ground = worldObj.getBlock(
                                blockX,
                                feetY - 1,
                                blockZ
                        );

                        if (ground == null
                                || !ground.getMaterial().blocksMovement()) {
                            continue;
                        }

                        double x = blockX + 0.5D;
                        double y = feetY;
                        double z = blockZ + 0.5D;

                        AxisAlignedBB clearance =
                                requireFormationClearance
                                        ? AxisAlignedBB.getBoundingBox(
                                        x - 2.0D,
                                        y,
                                        z - 3.25D,
                                        x + 2.0D,
                                        y + 2.25D,
                                        z + 3.25D
                                )
                                        : AxisAlignedBB.getBoundingBox(
                                        x - width * 0.5D,
                                        y,
                                        z - width * 0.5D,
                                        x + width * 0.5D,
                                        y + height,
                                        z + width * 0.5D
                                );

                        if (!worldObj.getCollidingBoundingBoxes(
                                this,
                                clearance
                        ).isEmpty()) {
                            continue;
                        }

                        getNavigator().clearPathEntity();
                        motionX = 0.0D;
                        motionY = 0.0D;
                        motionZ = 0.0D;
                        setLocationAndAngles(
                                x,
                                y,
                                z,
                                rotationYaw,
                                rotationPitch
                        );
                        prevPosX = posX;
                        prevPosY = posY;
                        prevPosZ = posZ;
                        lastTickPosX = posX;
                        lastTickPosY = posY;
                        lastTickPosZ = posZ;
                        fallDistance = 0.0F;
                        pathRequestCooldownTicks = 0;
                        lastPathRequestSucceeded = false;
                        /*
                         * Move chunk membership immediately. LOTR fast travel
                         * can leave the old player area hundreds or thousands
                         * of blocks behind in one tick, so waiting for a later
                         * entity update risks the old chunk unloading first.
                         */
                        worldObj.updateEntityWithOptionalForce(
                                this,
                                false
                        );

                        reconcileAttachedCrewFormation();
                        refreshFormationChunkBookkeepingAfterTeleport();
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void refreshFormationChunkBookkeepingAfterTeleport() {
        if (worldObj == null
                || worldObj.isRemote
                || !hasDurableOwnership()) {
            return;
        }

        SiegeRamCrewOwnershipData data =
                SiegeRamCrewOwnershipData.get(
                        worldObj,
                        false
                );

        if (data == null) {
            return;
        }

        data.touchRam(
                worldObj,
                getUniqueID(),
                ramGeneration,
                ((int)Math.floor(posX)) >> 4,
                ((int)Math.floor(posZ)) >> 4
        );

        for (int slot = 0; slot < CREW_SLOT_COUNT; ++slot) {
            LOTREntityNPC crew = crewReferences[slot];

            if (crew == null
                    || getValidAttachedCrewSlot(crew) != slot) {
                continue;
            }

            worldObj.updateEntityWithOptionalForce(
                    crew,
                    false
            );

            data.noteCarrierChunk(
                    worldObj,
                    getUniqueID(),
                    ramGeneration,
                    slot,
                    crew.getUniqueID(),
                    ((int)Math.floor(crew.posX)) >> 4,
                    ((int)Math.floor(crew.posZ)) >> 4
            );
        }
    }

    protected double getCrewSpeedMultiplier() {
        return getLivingCrewCount() / (double)CREW_SLOT_COUNT;
    }

    protected double getEffectiveMoveSpeed() {
        return BASE_RAM_MOVE_SPEED * getCrewSpeedMultiplier();
    }

    public int getLivingCrewCount() {
        if (worldObj != null && worldObj.isRemote) {
            return dataWatcher.getWatchableObjectInt(WATCHER_LIVING_CREW);
        }
        if (!crewConfigured) {
            return 0;
        }
        int living = 0;
        for (int slot = 0; slot < CREW_SLOT_COUNT; ++slot) {
            if (crewSlotAlive[slot]) {
                ++living;
            }
        }
        return living;
    }

    public boolean hasConfiguredCrew() {
        return crewConfigured;
    }

    public BattleRamState getRamState() {
        int ordinal = dataWatcher.getWatchableObjectInt(WATCHER_STATE);
        BattleRamState[] values = BattleRamState.values();
        return ordinal >= 0 && ordinal < values.length
                ? values[ordinal]
                : BattleRamState.FOLLOW_COMMANDER;
    }

    public void setRamState(BattleRamState state) {
        if (state == null) {
            state = BattleRamState.FOLLOW_COMMANDER;
        }
        dataWatcher.updateObject(WATCHER_STATE, state.ordinal());
        if (state != BattleRamState.ATTACK_GATE) {
            clearAttackCycleStart();
        }
        if (state == BattleRamState.FOLLOW_COMMANDER
                || state == BattleRamState.MOVE_TO_GATE) {
            resetPhysicalAttackRun();
        }
        if (state == BattleRamState.PAUSED) {
            getNavigator().clearPathEntity();
        }
    }

    /**
     * Returns the client animation phase in the authoritative 60-tick attack
     * cycle, or -1 when the ram should render its authored neutral pose.
     */
    public float getAttackAnimationPhaseTicks(float partialTicks) {
        /*
         * The old authored lunge has been retired. Attack motion now comes
         * from the authoritative entity positions themselves.
         */
        return -1.0F;
    }

    /**
     * Monotonically changing client-visible marker for real ram impacts.
     * Carrier presentation uses this only to trigger a brief inertial follow-
     * through; damage and collision remain fully server authoritative.
     */
    public int getRamImpactSerial() {
        return dataWatcher.getWatchableObjectInt(
                WATCHER_IMPACT_SERIAL
        );
    }

    private void markPhysicalRamImpact() {
        if (worldObj == null || worldObj.isRemote) {
            return;
        }

        int serial = dataWatcher.getWatchableObjectInt(
                WATCHER_IMPACT_SERIAL
        );

        dataWatcher.updateObject(
                WATCHER_IMPACT_SERIAL,
                Integer.valueOf(serial + 1)
        );
    }

    /**
     * Returns the small forward/backward presentation offset used by the
     * attached ram carriers during the authoritative attack cycle. Positive
     * values move toward the gate; negative values are the brief brace.
     *
     * This is intentionally pure math so the client crew renderer can share
     * the exact same 60-tick cycle without adding a second animation timer.
     */
    public static float getCrewAttackDriveOffset(float phaseTicks) {
        if (phaseTicks < 0.0F) {
            return 0.0F;
        }

        float phase = phaseTicks % ATTACK_INTERVAL_TICKS;
        if (phase < 0.0F) {
            phase += ATTACK_INTERVAL_TICKS;
        }

        if (phase < CREW_ATTACK_BRACE_END_TICK) {
            float progress = smoothPresentationStep(
                    phase / CREW_ATTACK_BRACE_END_TICK
            );
            return -CREW_ATTACK_BRACE_DISTANCE * progress;
        }

        if (phase < CREW_ATTACK_BRACE_RECOVER_END_TICK) {
            float progress = smoothPresentationStep(
                    (phase - CREW_ATTACK_BRACE_END_TICK)
                            / (CREW_ATTACK_BRACE_RECOVER_END_TICK
                            - CREW_ATTACK_BRACE_END_TICK)
            );
            return -CREW_ATTACK_BRACE_DISTANCE * (1.0F - progress);
        }

        if (phase < ATTACK_IMPACT_TICK) {
            float progress = smoothPresentationStep(
                    (phase - CREW_ATTACK_DRIVE_START_TICK)
                            / (ATTACK_IMPACT_TICK
                            - CREW_ATTACK_DRIVE_START_TICK)
            );
            return CREW_ATTACK_DRIVE_DISTANCE * progress;
        }

        if (phase < CREW_ATTACK_IMPACT_RECOVER_END_TICK) {
            float progress = smoothPresentationStep(
                    (phase - ATTACK_IMPACT_TICK)
                            / (CREW_ATTACK_IMPACT_RECOVER_END_TICK
                            - ATTACK_IMPACT_TICK)
            );
            return CREW_ATTACK_DRIVE_DISTANCE
                    + (CREW_ATTACK_LATE_RECOVER_DISTANCE
                    - CREW_ATTACK_DRIVE_DISTANCE) * progress;
        }

        float progress = smoothPresentationStep(
                (phase - CREW_ATTACK_IMPACT_RECOVER_END_TICK)
                        / (ATTACK_INTERVAL_TICKS
                        - CREW_ATTACK_IMPACT_RECOVER_END_TICK)
        );
        return CREW_ATTACK_LATE_RECOVER_DISTANCE * (1.0F - progress);
    }

    private static float smoothPresentationStep(float value) {
        float clamped = Math.max(0.0F, Math.min(1.0F, value));
        return clamped * clamped * (3.0F - 2.0F * clamped);
    }

    private void synchronizeAttackCycleStartFromPhase() {
        if (worldObj == null || worldObj.isRemote) {
            return;
        }

        int cycleStart =
                (int)(worldObj.getTotalWorldTime() - attackTicks);

        dataWatcher.updateObject(
                WATCHER_ATTACK_CYCLE_START,
                Integer.valueOf(cycleStart)
        );

        syncAttackPhaseWatcher();
    }

    private void syncAttackPhaseWatcher() {
        if (worldObj == null || worldObj.isRemote) {
            return;
        }

        float phase =
                attackTicks
                        + attackProgressRemainder;

        if (phase >= ATTACK_INTERVAL_TICKS) {
            phase %= ATTACK_INTERVAL_TICKS;
        }

        dataWatcher.updateObject(
                WATCHER_ATTACK_PHASE,
                Float.valueOf(phase)
        );
    }

    private void clearAttackCycleStart() {
        if (worldObj == null || worldObj.isRemote) {
            return;
        }

        if (dataWatcher.getWatchableObjectInt(WATCHER_ATTACK_CYCLE_START)
                != NO_ATTACK_CYCLE_START) {
            dataWatcher.updateObject(
                    WATCHER_ATTACK_CYCLE_START,
                    Integer.valueOf(NO_ATTACK_CYCLE_START)
            );
        }

        dataWatcher.updateObject(
                WATCHER_ATTACK_PHASE,
                Float.valueOf(0.0F)
        );
    }

    public LOTRFaction getRamFaction() {
        String factionName = dataWatcher.getWatchableObjectString(
                WATCHER_FACTION
        );
        return factionName.isEmpty()
                ? null
                : LOTRFaction.forName(factionName);
    }

    public void setRamFaction(LOTRFaction faction) {
        dataWatcher.updateObject(
                WATCHER_FACTION,
                faction == null ? "" : faction.codeName()
        );
    }

    public boolean isOwnFactionGate(
            TileEntitySiegeGate gate
    ) {
        if (gate == null) {
            return false;
        }

        LOTRFaction ramFaction =
                getRamFaction();

        LOTRFaction gateFaction =
                gate.getGateFaction();

        return ramFaction != null
                && gateFaction != null
                && ramFaction == gateFaction;
    }

    private boolean isAlliedFactionGate(
            TileEntitySiegeGate gate
    ) {
        if (gate == null) {
            return false;
        }

        LOTRFaction ramFaction = getRamFaction();
        LOTRFaction gateFaction = gate.getGateFaction();
        return ramFaction != null
                && gateFaction != null
                && ramFaction != gateFaction
                && ramFaction.isAlly(gateFaction);
    }

    private boolean shouldRefuseAlliedGate(
            TileEntitySiegeGate gate
    ) {
        if (!isAlliedFactionGate(gate)) {
            return false;
        }

        /*
         * Match RamControlManager's intentional administrative override while
         * continuously enforcing ordinary faction diplomacy. The override is
         * only meaningful while an administrative commander is actually
         * present; otherwise an allied gate is treated as friendly.
         */
        EntityPlayer commander = getCommander();
        return !(commander instanceof EntityPlayerMP)
                || !GateAccess.isAdministrativePlayer(
                (EntityPlayerMP)commander
        );
    }

    /**
     * Marks this detached client-side instance as LOTR's unit-hire preview.
     * The larger logical size is preview-only and lets the native hire GUI
     * scale the complete ram-and-carrier formation instead of only the ram.
     */
    public void configureHirePreview(LOTRFaction faction) {
        if (worldObj == null || !worldObj.isRemote) {
            return;
        }
        setRamFaction(faction);
        hirePreview = true;
        setSize(4.2F, 2.2F);
    }

    public boolean isHirePreview() {
        return hirePreview;
    }

    public static double getCrewLocalX(int slot) {
        return slot < 0 || slot >= CREW_SLOT_COUNT
                ? 0.0D
                : CREW_OFFSETS[slot][0];
    }

    public static double getCrewLocalZ(int slot) {
        return slot < 0 || slot >= CREW_SLOT_COUNT
                ? 0.0D
                : CREW_OFFSETS[slot][1];
    }

    public UUID getCommanderUuid() {
        return commanderUuid;
    }

    public boolean hasGateTarget() {
        return dataWatcher.getWatchableObjectByte(WATCHER_HAS_TARGET) != 0;
    }

    public int getTargetControllerX() {
        return dataWatcher.getWatchableObjectInt(WATCHER_TARGET_X);
    }

    public int getTargetControllerY() {
        return dataWatcher.getWatchableObjectInt(WATCHER_TARGET_Y);
    }

    public int getTargetControllerZ() {
        return dataWatcher.getWatchableObjectInt(WATCHER_TARGET_Z);
    }

    public UUID getTargetGateUuid() {
        return targetGateUuid;
    }

    public boolean isTargeting(TileEntitySiegeGate gate) {
        return gate != null
                && hasGateTarget()
                && gate.getWorldObj() == worldObj
                && gate.xCoord == getTargetControllerX()
                && gate.yCoord == getTargetControllerY()
                && gate.zCoord == getTargetControllerZ();
    }

    public boolean isQueuedTarget(TileEntitySiegeGate gate) {
        return getTargetQueueIndex(gate) >= 0;
    }

    public int getTargetQueueIndex(TileEntitySiegeGate gate) {
        if (gate == null || gate.getWorldObj() != worldObj) {
            return -1;
        }
        for (int i = 0; i < targetQueue.size(); ++i) {
            if (targetQueue.get(i).matches(gate)) {
                return i;
            }
        }
        return -1;
    }

    public int getTargetQueueSize() {
        return targetQueue.size();
    }

    public List<TargetQueueSnapshot> getTargetQueueSnapshot() {
        List<TargetQueueSnapshot> snapshot =
                new ArrayList<TargetQueueSnapshot>();
        for (QueuedGateTarget target : targetQueue) {
            snapshot.add(new TargetQueueSnapshot(
                    target.dimensionId,
                    target.controllerX,
                    target.controllerY,
                    target.controllerZ
            ));
        }
        return snapshot;
    }

    public boolean assignGateTarget(TileEntitySiegeGate gate) {
        return queueGateTarget(gate);
    }

    public boolean queueGateTarget(TileEntitySiegeGate gate) {
        if (isQueuedTarget(gate)) {
            return true;
        }
        if (worldObj == null
                || worldObj.isRemote
                || gate == null
                || gate.getWorldObj() != worldObj
                || isOwnFactionGate(gate)
                || targetQueue.size() >= MAX_TARGET_QUEUE_SIZE
                || !gate.tryReserveForRam(getUniqueID())) {
            return false;
        }

        boolean wasEmpty = targetQueue.isEmpty();
        targetQueue.add(QueuedGateTarget.fromGate(gate));

        if (wasEmpty) {
            activateTargetQueueHead();
        }
        return true;
    }

    public boolean removeGateTargetFromQueue(TileEntitySiegeGate gate) {
        int index = getTargetQueueIndex(gate);
        if (index < 0) {
            return false;
        }

        QueuedGateTarget removed = targetQueue.remove(index);
        clearReservationForEntry(removed);

        if (index == 0) {
            clearActiveTargetData();
            if (targetQueue.isEmpty()) {
                pauseRamWithoutResumeTarget();
            } else {
                activateTargetQueueHead();
            }
        }
        return true;
    }

    public void cancelGateTarget() {
        releaseGateReservation();
        clearTargetData();
        pauseRamWithoutResumeTarget();
    }

    private void refreshAssignedGateReservation() {
        /*
         * A paused or currently unmanned ram is not actively prosecuting its
         * siege assignment. Do not keep renewing its reservations forever;
         * TileEntitySiegeGate's existing bounded reservation timeout will
         * release them naturally. A quick pause can therefore resume without
         * disruption, while a long-idle ram cannot monopolize a gate.
         */
        if (getRamState() == BattleRamState.PAUSED
                || getLivingCrewCount() <= 0) {
            return;
        }

        if (targetQueue.isEmpty()) {
            if (hasGateTarget()) {
                targetQueue.add(new QueuedGateTarget(
                        targetDimension,
                        getTargetControllerX(),
                        getTargetControllerY(),
                        getTargetControllerZ(),
                        targetGateUuid
                ));
            } else {
                return;
            }
        }

        QueuedGateTarget head = targetQueue.get(0);
        if (!activeTargetMatches(head)) {
            activateTargetQueueHead();
            head = targetQueue.get(0);
        }

        if (isQueuedCoordinateLoaded(head)) {
            TileEntitySiegeGate gate = getLoadedQueuedGate(head);
            if (gate == null
                    || (head.gateUuid != null
                    && !head.gateUuid.equals(gate.getGateUuid()))) {
                abandonTarget("The assigned gate no longer exists.");
                return;
            }
            if (gate.getGateState() == GateState.BREACHED) {
                finishBreachedTarget(gate);
                return;
            }
            if (isOwnFactionGate(gate)) {
                abandonTarget(
                        "Your units refuse to attack their own faction."
                );
                return;
            }
            if (shouldRefuseAlliedGate(gate)) {
                abandonTarget(
                        "Friendly or allied Siege Gates cannot be targeted."
                );
                return;
            }
            if (!gate.refreshRamReservation(getUniqueID())
                    && !gate.tryReserveForRam(getUniqueID())) {
                abandonTarget(
                        "The assigned gate is reserved by another ram."
                );
                return;
            }
        }

        for (int i = targetQueue.size() - 1; i >= 1; --i) {
            QueuedGateTarget queued = targetQueue.get(i);
            if (!isQueuedCoordinateLoaded(queued)) {
                continue;
            }

            TileEntitySiegeGate gate = getLoadedQueuedGate(queued);
            if (gate == null
                    || (queued.gateUuid != null
                    && !queued.gateUuid.equals(gate.getGateUuid()))
                    || gate.getGateState() == GateState.BREACHED) {
                clearReservationForEntry(queued);
                targetQueue.remove(i);
                RamControlManager.syncTargetQueueToCommander(this);
                continue;
            }

            if (isOwnFactionGate(gate)) {
                clearReservationForEntry(queued);
                targetQueue.remove(i);
                RamControlManager.syncTargetQueueToCommander(this);
                sendCommanderMessage(
                        "Your units refuse to attack their own faction. "
                                + "That queued gate was removed."
                );
                continue;
            }

            if (shouldRefuseAlliedGate(gate)) {
                clearReservationForEntry(queued);
                targetQueue.remove(i);
                RamControlManager.syncTargetQueueToCommander(this);
                sendCommanderMessage(
                        "A friendly or allied queued Siege Gate was removed."
                );
                continue;
            }

            if (!gate.refreshRamReservation(getUniqueID())
                    && !gate.tryReserveForRam(getUniqueID())) {
                targetQueue.remove(i);
                RamControlManager.syncTargetQueueToCommander(this);
            }
        }
    }

    private void updateMoveToGate() {
        if (!hasGateTarget()) {
            pauseRamWithoutResumeTarget();
            return;
        }
        if (getLivingCrewCount() <= 0) {
            getNavigator().clearPathEntity();
            resetNoProgressWhileWaitingForCrew();
            return;
        }
        TileEntitySiegeGate gate = getLoadedTargetGate();
        if (gate == null) {
            ++pathFailureTicks;
            getNavigator().clearPathEntity();
            if (pathFailureTicks >= TARGET_FAILURE_TIMEOUT_TICKS) {
                abandonTarget("The assigned gate could not be reached.");
            }
            return;
        }
        if (gate.getGateState() == GateState.BREACHED) {
            finishBreachedTarget(gate);
            return;
        }

        AttackPoint point = calculateAttackPoint(gate);
        if (point == null) {
            abandonTarget("The assigned gate has no valid impact point.");
            return;
        }
        double horizontalDistance = getHorizontalApproachDistance(point);
        if (isWithinAttackPosition(
                point,
                ATTACK_ALIGNMENT_START_DISTANCE,
                ATTACK_POSITION_VERTICAL_TOLERANCE
        )) {
            getNavigator().clearPathEntity();
            lastPathRequestSucceeded = false;

            /*
             * PathNavigate is intentionally only responsible for the coarse
             * approach. The final short alignment is deterministic so every
             * strike begins on the selected gate lane and perpendicular to
             * its plane.
             */
            if (alignExactlyForAttack(point)) {
                attackTicks = 0;
                attackProgressRemainder = 0.0F;
                setRamState(BattleRamState.ATTACK_GATE);
                beginPhysicalAttackRun(point);
            }
            return;
        }

        if (pathRequestCooldownTicks > 0) {
            --pathRequestCooldownTicks;
        }
        if (getNavigator().noPath()
                && pathRequestCooldownTicks <= 0) {
            double speedMultiplier = getCrewSpeedMultiplier();
            lastPathRequestSucceeded = getNavigator().tryMoveToXYZ(
                    point.approachX,
                    point.approachY,
                    point.approachZ,
                    speedMultiplier
            );
            if (lastPathRequestSucceeded) {
                consecutivePathRequestFailures = 0;
                pathRequestCooldownTicks = PATH_RETRY_INTERVAL_TICKS;
            } else {
                pathRequestFailedSinceProgressCheck = true;
                ++consecutivePathRequestFailures;
                getNavigator().clearPathEntity();

                /*
                 * A direct PathNavigate rejection is much stronger evidence
                 * than ordinary slow/no progress. Do not make a newly
                 * assigned ram stare at an unreachable center lane for the
                 * full multi-second progress timeout before trying the next
                 * real gate column. Give the request one short retry in case
                 * navigation state was transient, then reject this lane.
                 */
                if (consecutivePathRequestFailures
                        >= MAX_CONSECUTIVE_PATH_REQUEST_FAILURES) {
                    failCurrentAttackLane();
                    resetPathProgressForNewLane();
                    return;
                } else {
                    pathRequestCooldownTicks =
                            FAILED_PATH_RETRY_INTERVAL_TICKS;
                }
            }
        }
        updatePathProgress(gate, horizontalDistance);
    }

    private void updateAttackGate() {
        if (!hasGateTarget()) {
            pauseRamWithoutResumeTarget();
            return;
        }

        TileEntitySiegeGate gate = getLoadedTargetGate();
        if (gate == null) {
            resetPhysicalAttackRun();
            setRamState(BattleRamState.MOVE_TO_GATE);
            return;
        }

        if (gate.getGateState() == GateState.BREACHED) {
            finishBreachedTarget(gate);
            return;
        }

        if (getLivingCrewCount() <= 0) {
            getNavigator().clearPathEntity();
            motionX = 0.0D;
            motionZ = 0.0D;
            return;
        }

        AttackPoint point = calculateAttackPoint(gate);
        if (point == null) {
            abandonTarget("The assigned gate has no valid impact point.");
            return;
        }

        faceImpactPoint(point);

        if (physicalAttackPhase == PhysicalAttackPhase.NONE) {
            if (!isStrictlyAlignedForAttack(point)) {
                resetPhysicalAttackRun();
                prepareForGateRepath();
                setRamState(BattleRamState.MOVE_TO_GATE);
                return;
            }
            beginPhysicalAttackRun(point);
        }

        if (getAttackCenterlineOffset(point)
                > ATTACK_CENTERLINE_TOLERANCE) {
            resetPhysicalAttackRun();
            prepareForGateRepath();
            setRamState(BattleRamState.MOVE_TO_GATE);
            return;
        }

        if (physicalAttackPhase == PhysicalAttackPhase.READYING) {
            updatePhysicalAttackReadying(point);

        } else if (physicalAttackPhase == PhysicalAttackPhase.BACKING_UP) {
            updatePhysicalAttackBacking(point);

        } else if (physicalAttackPhase == PhysicalAttackPhase.BRACING) {
            updatePhysicalAttackBracing(point);

        } else if (physicalAttackPhase == PhysicalAttackPhase.CHARGING) {
            updatePhysicalAttackCharge(gate, point);

        } else if (physicalAttackPhase == PhysicalAttackPhase.RECOVERING) {
            updatePhysicalAttackRecovery(point);
        }

        /*
         * The physical X/Z movement is independent from rotationYaw. Keep the
         * ram and every carrier facing the gate while the formation genuinely
         * travels backward and forward through the world.
         */
        faceImpactPoint(point);
        reconcileAttachedCrewFormation();
    }

    private void beginPhysicalAttackRun(AttackPoint point) {
        resetPhysicalAttackRun();

        if (point == null) {
            return;
        }

        getNavigator().clearPathEntity();
        motionX = 0.0D;
        motionZ = 0.0D;

        /*
         * Pause at the gate before the crew starts hauling backward.  The
         * short ready beat makes the sequence read as a deliberate operation
         * instead of an entity instantly reversing direction.
         */
        physicalAttackPhase = PhysicalAttackPhase.READYING;
        attackPhysicalPhaseTicks = ATTACK_READY_TICKS;
        attackPhysicalBlockedTicks = 0;
    }

    private void updatePhysicalAttackReadying(AttackPoint point) {
        getNavigator().clearPathEntity();
        motionX = 0.0D;
        motionZ = 0.0D;

        if (attackPhysicalPhaseTicks > 0) {
            --attackPhysicalPhaseTicks;
            return;
        }

        beginPhysicalAttackBacking(point);
    }

    private void beginPhysicalAttackBacking(AttackPoint point) {
        if (point == null) {
            return;
        }

        double normalX = point.approachX - point.impactX;
        double normalZ = point.approachZ - point.impactZ;
        double normalLength = Math.sqrt(
                normalX * normalX + normalZ * normalZ
        );

        if (normalLength < 1.0E-6D) {
            physicalAttackPhase = PhysicalAttackPhase.RECOVERING;
            attackPhysicalPhaseTicks = ATTACK_RECOVERY_TICKS;
            return;
        }

        normalX /= normalLength;
        normalZ /= normalLength;

        attackRetreatDistance = ATTACK_BACKUP_PREFERRED_DISTANCE;
        attackRetreatX =
                point.approachX + normalX * attackRetreatDistance;
        attackRetreatZ =
                point.approachZ + normalZ * attackRetreatDistance;

        physicalAttackPhase = PhysicalAttackPhase.BACKING_UP;
        attackPhysicalPhaseTicks = 0;
        attackPhysicalBlockedTicks = 0;
    }

    private void updatePhysicalAttackBacking(AttackPoint point) {
        getNavigator().clearPathEntity();
        ++attackPhysicalPhaseTicks;

        double dx = attackRetreatX - posX;
        double dz = attackRetreatZ - posZ;
        double distance = Math.sqrt(dx * dx + dz * dz);

        if (distance <= ATTACK_RETREAT_ARRIVAL_TOLERANCE) {
            beginPhysicalAttackBrace(point);
            return;
        }

        double moved = movePhysicalAttackToward(
                attackRetreatX,
                attackRetreatZ,
                getPhysicalAttackBackupStep(point)
        );

        if (moved < 1.0E-4D) {
            ++attackPhysicalBlockedTicks;
        } else {
            attackPhysicalBlockedTicks = 0;
        }

        if (attackPhysicalBlockedTicks
                >= ATTACK_BLOCKED_TICKS_BEFORE_SHORT_CHARGE) {

            double backedDistance =
                    getAttackBackedDistance(point);

            if (backedDistance >= ATTACK_BACKUP_MIN_DISTANCE) {
                /*
                 * A wall, cliff edge, or other obstruction shortened the
                 * available lane. Charge from the real distance the unit
                 * managed to create instead of pretending it reached four
                 * blocks.
                 */
                beginPhysicalAttackBrace(point);
            } else {
                failCurrentAttackLane();
                resetPhysicalAttackRun();
                prepareForGateRepath();
                setRamState(BattleRamState.MOVE_TO_GATE);
            }
            return;
        }

        if (attackPhysicalPhaseTicks >= ATTACK_PHASE_FAILURE_TICKS) {
            double backedDistance = getAttackBackedDistance(point);
            if (backedDistance >= ATTACK_BACKUP_MIN_DISTANCE) {
                beginPhysicalAttackBrace(point);
            } else {
                failCurrentAttackLane();
                resetPhysicalAttackRun();
                prepareForGateRepath();
                setRamState(BattleRamState.MOVE_TO_GATE);
            }
        }
    }

    private void beginPhysicalAttackBrace(AttackPoint point) {
        getNavigator().clearPathEntity();
        motionX = 0.0D;
        motionZ = 0.0D;

        physicalAttackPhase = PhysicalAttackPhase.BRACING;
        attackPhysicalPhaseTicks = ATTACK_REAR_BRACE_TICKS;
        attackPhysicalBlockedTicks = 0;
    }

    private void updatePhysicalAttackBracing(AttackPoint point) {
        getNavigator().clearPathEntity();
        motionX = 0.0D;
        motionZ = 0.0D;

        if (attackPhysicalPhaseTicks > 0) {
            --attackPhysicalPhaseTicks;
            return;
        }

        beginPhysicalAttackCharge(point);
    }

    private void beginPhysicalAttackCharge(AttackPoint point) {
        getNavigator().clearPathEntity();
        motionX = 0.0D;
        motionZ = 0.0D;

        attackChargeStartDistance = getHorizontalApproachDistance(point);
        physicalAttackPhase = PhysicalAttackPhase.CHARGING;
        attackPhysicalPhaseTicks = 0;
        attackPhysicalBlockedTicks = 0;
    }

    private void updatePhysicalAttackCharge(
            TileEntitySiegeGate gate,
            AttackPoint point
    ) {
        getNavigator().clearPathEntity();
        ++attackPhysicalPhaseTicks;

        double distance = getHorizontalApproachDistance(point);

        if (distance <= ATTACK_CHARGE_ARRIVAL_TOLERANCE) {
            if (Math.abs(posY - point.approachY)
                    > ATTACK_POSITION_VERTICAL_TOLERANCE) {
                failCurrentAttackLane();
                resetPhysicalAttackRun();
                prepareForGateRepath();
                setRamState(BattleRamState.MOVE_TO_GATE);
                return;
            }

            getNavigator().clearPathEntity();
            motionX = 0.0D;
            motionZ = 0.0D;
            faceImpactPoint(point);
            applyPhysicalRamImpact(gate, point);
            return;
        }

        double moved = movePhysicalAttackToward(
                point.approachX,
                point.approachZ,
                getPhysicalAttackChargeStep(point)
        );

        if (moved < 1.0E-4D) {
            ++attackPhysicalBlockedTicks;
        } else {
            attackPhysicalBlockedTicks = 0;
        }

        if (attackPhysicalBlockedTicks
                >= ATTACK_BLOCKED_TICKS_BEFORE_SHORT_CHARGE
                || attackPhysicalPhaseTicks >= ATTACK_PHASE_FAILURE_TICKS) {

            failCurrentAttackLane();
            resetPhysicalAttackRun();
            prepareForGateRepath();
            setRamState(BattleRamState.MOVE_TO_GATE);
        }
    }

    private double movePhysicalAttackToward(
            double targetX,
            double targetZ,
            double maximumStep
    ) {
        if (maximumStep <= 0.0D) {
            return 0.0D;
        }

        double dx = targetX - posX;
        double dz = targetZ - posZ;
        double distance = Math.sqrt(dx * dx + dz * dz);

        if (distance < 1.0E-6D) {
            return 0.0D;
        }

        double step = Math.min(maximumStep, distance);
        double beforeX = posX;
        double beforeZ = posZ;

        /*
         * Move along the exact attack axis independently of rotationYaw. That
         * is what lets the entity genuinely back up while still facing the
         * gate. Entity.moveEntity keeps normal collision/step/fall handling;
         * this is authoritative movement, not a render translation.
         */
        moveAttackHorizontally(
                dx / distance * step,
                dz / distance * step
        );

        motionX = 0.0D;
        motionZ = 0.0D;

        double movedX = posX - beforeX;
        double movedZ = posZ - beforeZ;

        return Math.sqrt(
                movedX * movedX + movedZ * movedZ
        );
    }

    private void moveAttackHorizontally(
            double deltaX,
            double deltaZ
    ) {
        float previousStepHeight = stepHeight;

        if (stepHeight < ATTACK_TERRAIN_STEP_HEIGHT) {
            stepHeight = ATTACK_TERRAIN_STEP_HEIGHT;
        }

        try {
            moveEntity(
                    deltaX,
                    0.0D,
                    deltaZ
            );
        } finally {
            /*
             * Do not change the ram's ordinary follow/pathing capabilities.
             * The extra step height belongs only to the deterministic gate
             * attack movement that otherwise cannot use JumpHelper.
             */
            stepHeight = previousStepHeight;
        }
    }

    private double getChargeDamageMultiplier() {
        if (attackChargeStartDistance >= ATTACK_FULL_CHARGE_DISTANCE) {
            return 1.0D;
        }
        if (attackChargeStartDistance >= ATTACK_MEDIUM_CHARGE_DISTANCE) {
            return ATTACK_MEDIUM_DAMAGE_MULTIPLIER;
        }
        return ATTACK_SHORT_DAMAGE_MULTIPLIER;
    }

    private void applyPhysicalRamImpact(
            TileEntitySiegeGate gate,
            AttackPoint point
    ) {
        markPhysicalRamImpact();

        int effectiveSiegeDamage = (int)Math.round(
                MumakilConfig.ramSiegeDamage
                        * getCrewSpeedMultiplier()
                        * getChargeDamageMultiplier()
        );
        boolean damageApplied =
                gate.applySiegeDamage(effectiveSiegeDamage);

        if (damageApplied) {
            boolean breached =
                    gate.getGateState() == GateState.BREACHED;

            worldObj.playSoundEffect(
                    point.impactX,
                    point.impactY,
                    point.impactZ,
                    "lotrmoremobs:siege.ram_hit",
                    breached ? 1.35F : 1.2F,
                    0.94F + rand.nextFloat() * 0.12F
            );

            if (!breached) {
                spawnGateImpactParticles(point);
            }
        }

        if (gate.getGateState() == GateState.BREACHED) {
            resetPhysicalAttackRun();
            finishBreachedTarget(gate);
            return;
        }

        physicalAttackPhase = PhysicalAttackPhase.RECOVERING;
        attackPhysicalPhaseTicks = ATTACK_RECOVERY_TICKS;
        attackPhysicalBlockedTicks = 0;
    }

    private void updatePhysicalAttackRecovery(AttackPoint point) {
        getNavigator().clearPathEntity();
        motionX = 0.0D;
        motionZ = 0.0D;

        if (attackPhysicalPhaseTicks > 0) {
            --attackPhysicalPhaseTicks;
            return;
        }

        if (!isStrictlyAlignedForAttack(point)) {
            resetPhysicalAttackRun();
            prepareForGateRepath();
            setRamState(BattleRamState.MOVE_TO_GATE);
            return;
        }

        beginPhysicalAttackRun(point);
    }

    private double getPhysicalAttackBackupStep(AttackPoint point) {
        double progress = attackRetreatDistance <= 1.0E-6D
                ? 0.0D
                : Math.max(
                0.0D,
                Math.min(
                        1.0D,
                        getAttackBackedDistance(point)
                                / attackRetreatDistance
                )
        );

        /*
         * Walk the machine out under control: slow first step, a steadier
         * middle, then ease down as the crew reaches the rear mark.
         */
        double envelope = Math.sin(Math.PI * progress);
        double edge = ATTACK_RETREAT_START_SPEED_FACTOR
                + (ATTACK_RETREAT_END_SPEED_FACTOR
                - ATTACK_RETREAT_START_SPEED_FACTOR) * progress;
        double phaseFactor = edge + (1.0D - edge) * envelope;

        return BASE_RAM_MOVE_SPEED
                * getCrewSpeedMultiplier()
                * ATTACK_BACKUP_SPEED_FACTOR
                * phaseFactor;
    }

    private double getPhysicalAttackChargeStep(AttackPoint point) {
        double remaining = getHorizontalApproachDistance(point);
        double progress = attackChargeStartDistance <= 1.0E-6D
                ? 1.0D
                : Math.max(
                0.0D,
                Math.min(
                        1.0D,
                        1.0D - remaining / attackChargeStartDistance
                )
        );

        /* Smooth acceleration from a heavy first shove into the full run. */
        double eased = progress * progress * (3.0D - 2.0D * progress);
        double phaseFactor = ATTACK_CHARGE_START_SPEED_FACTOR
                + (1.0D - ATTACK_CHARGE_START_SPEED_FACTOR) * eased;

        return BASE_RAM_MOVE_SPEED
                * getCrewSpeedMultiplier()
                * ATTACK_CHARGE_SPEED_FACTOR
                * phaseFactor;
    }

    private double getAttackCenterlineOffset(AttackPoint point) {
        if (point == null) {
            return Double.MAX_VALUE;
        }

        double normalX = point.approachX - point.impactX;
        double normalZ = point.approachZ - point.impactZ;
        double length = Math.sqrt(normalX * normalX + normalZ * normalZ);

        if (length < 1.0E-6D) {
            return Double.MAX_VALUE;
        }

        normalX /= length;
        normalZ /= length;

        double lateralX = -normalZ;
        double lateralZ = normalX;

        double fromApproachX = posX - point.approachX;
        double fromApproachZ = posZ - point.approachZ;

        return Math.abs(
                fromApproachX * lateralX
                        + fromApproachZ * lateralZ
        );
    }

    private double getAttackBackedDistance(AttackPoint point) {
        if (point == null) {
            return 0.0D;
        }

        double normalX = point.approachX - point.impactX;
        double normalZ = point.approachZ - point.impactZ;
        double length = Math.sqrt(normalX * normalX + normalZ * normalZ);

        if (length < 1.0E-6D) {
            return 0.0D;
        }

        normalX /= length;
        normalZ /= length;

        return Math.max(
                0.0D,
                (posX - point.approachX) * normalX
                        + (posZ - point.approachZ) * normalZ
        );
    }

    private void resetPhysicalAttackRun() {
        physicalAttackPhase = PhysicalAttackPhase.NONE;
        attackRetreatX = 0.0D;
        attackRetreatZ = 0.0D;
        attackRetreatDistance = 0.0D;
        attackChargeStartDistance = 0.0D;
        attackPhysicalPhaseTicks = 0;
        attackPhysicalBlockedTicks = 0;
        attackTicks = 0;
        attackProgressRemainder = 0.0F;
        clearAttackCycleStart();
    }

    private void updatePathProgress(
            TileEntitySiegeGate gate,
            double horizontalDistance
    ) {
        if (lastPathDistance == Double.MAX_VALUE) {
            lastPathDistance = horizontalDistance;
        }
        if (Double.isNaN(lastPathProgressX)
                || Double.isNaN(lastPathProgressZ)) {
            lastPathProgressX = posX;
            lastPathProgressZ = posZ;
        }
        ++pathProgressCheckTicks;
        if (pathProgressCheckTicks >= PATH_PROGRESS_CHECK_TICKS) {
            int evaluatedTicks = pathProgressCheckTicks;
            double improvement = lastPathDistance - horizontalDistance;
            double traveledX = posX - lastPathProgressX;
            double traveledZ = posZ - lastPathProgressZ;
            double horizontalTravel = Math.sqrt(
                    traveledX * traveledX + traveledZ * traveledZ
            );
            double crewMovementScale = Math.max(
                    0.10D,
                    getCrewSpeedMultiplier()
            );
            double requiredImprovement =
                    PATH_PROGRESS_EPSILON * crewMovementScale;
            double requiredTravel =
                    PATH_TRAVEL_EPSILON * crewMovementScale;

            /*
             * Re-evaluate a genuinely stalled lane quickly. Meaningful
             * approach progress fully clears the failure timer. Horizontal
             * travel that is not yet closing on the lane receives extra
             * detour grace, but still accumulates failure slowly so circling
             * cannot preserve a bad lane forever. Scale the thresholds with
             * living crew so partially crewed rams are not mistaken for
             * stalled simply because their intended movement is slower.
             */
            if (improvement >= requiredImprovement) {
                pathFailureTicks = 0;
            } else if (lastPathRequestSucceeded
                    || pathRequestFailedSinceProgressCheck
                    || getNavigator().noPath()) {
                pathFailureTicks += horizontalTravel >= requiredTravel
                        ? Math.max(1, evaluatedTicks / 2)
                        : evaluatedTicks;
            }
            lastPathDistance = horizontalDistance;
            lastPathProgressX = posX;
            lastPathProgressZ = posZ;
            pathProgressCheckTicks = 0;
            pathRequestFailedSinceProgressCheck = false;

            /*
             * A failed route invalidates only the current lane. The next
             * calculateAttackPoint() call prefers another lane on this face;
             * the opposite face is considered only after every lane here is
             * exhausted.
             */
            if (pathFailureTicks >= OPPOSITE_SIDE_RETRY_TICKS) {
                failCurrentAttackLane();
                resetPathProgressForNewLane();
                getNavigator().clearPathEntity();

                /*
                 * Resolve the next lane immediately enough to detect a gate
                 * whose current and opposite faces have both been exhausted,
                 * but do not issue a PathNavigate search here.
                 */
                if (calculateAttackPoint(gate) == null) {
                    abandonTarget("The assigned gate could not be reached.");
                }
            }
        }
    }

    private double getHorizontalApproachDistance(AttackPoint point) {
        double dx = point.approachX - posX;
        double dz = point.approachZ - posZ;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private boolean isWithinAttackPosition(
            AttackPoint point,
            double horizontalTolerance,
            double verticalTolerance
    ) {
        if (point == null
                || horizontalTolerance < 0.0D
                || verticalTolerance < 0.0D) {
            return false;
        }
        double approachDirectionX = point.approachX - point.impactX;
        double approachDirectionZ = point.approachZ - point.impactZ;
        double ramFromImpactX = posX - point.impactX;
        double ramFromImpactZ = posZ - point.impactZ;
        boolean onCorrectSide = ramFromImpactX * approachDirectionX
                + ramFromImpactZ * approachDirectionZ > 0.0D;
        return onCorrectSide
                && getHorizontalApproachDistance(point)
                <= horizontalTolerance
                && Math.abs(posY - point.approachY) <= verticalTolerance;
    }

    private void spawnGateImpactParticles(
            AttackPoint point
    ) {
        if (!(worldObj instanceof WorldServer)
                || point == null
                || point.impactPart == null) {

            return;
        }

        GatePartData part =
                point.impactPart;

        Block sourceBlock =
                part.getSourceBlock();

        if (sourceBlock == null) {
            return;
        }

        WorldServer serverWorld =
                (WorldServer)worldObj;

        String particleName =
                "blockcrack_"
                        + Block.getIdFromBlock(
                        sourceBlock
                )
                        + "_"
                        + part.getSourceMetadata();

        serverWorld.func_147487_a(
                particleName,
                point.impactX,
                point.impactY,
                point.impactZ,
                6,
                0.25D,
                0.20D,
                0.25D,
                0.055D
        );

        serverWorld.func_147487_a(
                "smoke",
                point.impactX,
                point.impactY,
                point.impactZ,
                2,
                0.18D,
                0.12D,
                0.18D,
                0.015D
        );
    }

    private AttackPoint calculateAttackPoint(TileEntitySiegeGate gate) {
        List<GatePartData> parts = gate.getGateParts();
        if (parts.isEmpty()) {
            return null;
        }

        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE;

        for (GatePartData part : parts) {
            minX = Math.min(minX, part.getRelativeX());
            maxX = Math.max(maxX, part.getRelativeX());
            minZ = Math.min(minZ, part.getRelativeZ());
            maxZ = Math.max(maxZ, part.getRelativeZ());
            minY = Math.min(minY, part.getRelativeY());
        }

        GateOrientation orientation = gate.getGateOrientation();
        if (orientation == null) {
            orientation = maxX - minX >= maxZ - minZ
                    ? GateOrientation.WIDTH_X
                    : GateOrientation.WIDTH_Z;
        }

        double centerX =
                gate.xCoord + (minX + maxX + 1) * 0.5D;
        double centerZ =
                gate.zCoord + (minZ + maxZ + 1) * 0.5D;

        if (attackSideSign == 0) {
            attackSideSign = orientation == GateOrientation.WIDTH_X
                    ? (posZ < centerZ ? -1 : 1)
                    : (posX < centerX ? -1 : 1);
            resetAttackLaneSelectionForFace();
        }

        for (int faceAttempt = 0; faceAttempt < 2; ++faceAttempt) {
            if (attackLaneCoordinate != NO_ATTACK_LANE
                    && !isAttackLaneFailed(attackLaneCoordinate)) {
                AttackPoint current = createAttackPointForLane(
                        gate,
                        parts,
                        orientation,
                        minX,
                        maxX,
                        minZ,
                        maxZ,
                        minY,
                        attackSideSign,
                        attackLaneCoordinate
                );
                if (current != null) {
                    return current;
                }

                /* Gate geometry changed while this target was active. */
                attackLaneCoordinate = NO_ATTACK_LANE;
                attackAlignmentFailureTicks = 0;
            }

            int candidate = findBestAvailableAttackLaneCoordinate(
                    parts,
                    orientation,
                    minX,
                    maxX,
                    minZ,
                    maxZ,
                    minY,
                    attackSideSign
            );

            if (candidate != NO_ATTACK_LANE) {
                attackLaneCoordinate = candidate;
                attackAlignmentFailureTicks = 0;
                return createAttackPointForLane(
                        gate,
                        parts,
                        orientation,
                        minX,
                        maxX,
                        minZ,
                        maxZ,
                        minY,
                        attackSideSign,
                        candidate
                );
            }

            if (triedOppositeSide) {
                return null;
            }

            /*
             * Exhaust the meaningfully distinct attack corridors on the
             * chosen face before trying the opposite face. Adjacent columns
             * around a failed corridor are deliberately skipped below.
             */
            triedOppositeSide = true;
            attackSideSign = -attackSideSign;
            resetAttackLaneSelectionForFace();
            resetPathProgressForNewLane();
            getNavigator().clearPathEntity();
        }

        return null;
    }

    private int findBestAvailableAttackLaneCoordinate(
            List<GatePartData> parts,
            GateOrientation orientation,
            int minX,
            int maxX,
            int minZ,
            int maxZ,
            int minY,
            int sideSign
    ) {
        int faceCoordinate = orientation == GateOrientation.WIDTH_X
                ? (sideSign < 0 ? minZ : maxZ)
                : (sideSign < 0 ? minX : maxX);
        double centerCoordinate = orientation == GateOrientation.WIDTH_X
                ? (minX + maxX + 1) * 0.5D
                : (minZ + maxZ + 1) * 0.5D;
        boolean selectingAlternate =
                !failedAttackLaneCoordinates.isEmpty();

        if (failedAttackLaneCoordinates.size()
                >= ATTACK_MAX_DISTINCT_LANES_PER_FACE) {
            return NO_ATTACK_LANE;
        }

        /*
         * Prefer bottom-row columns on the selected outer face. If an
         * irregular/thick gate has no bottom block on that face, preserve the
         * old source-block fallback by considering bottom-row columns from
         * the remaining thickness before giving up on the face entirely.
         *
         * The initial lane remains center-first. After a real lane failure,
         * however, adjacent columns are not useful alternatives for a machine
         * this large. Only meaningfully separated candidates are eligible,
         * and the candidate farthest from the nearest failed lane is preferred
         * before center distance is used as a tie-breaker.
         */
        for (int pass = 0; pass < 2; ++pass) {
            boolean requireOuterFace = pass == 0;
            int bestCoordinate = NO_ATTACK_LANE;
            int bestFailedLaneSeparation = -1;
            double bestCenterDistance = Double.MAX_VALUE;

            for (GatePartData part : parts) {
                if (part.getRelativeY() != minY) {
                    continue;
                }
                if (requireOuterFace
                        && orientation == GateOrientation.WIDTH_X
                        && part.getRelativeZ() != faceCoordinate) {
                    continue;
                }
                if (requireOuterFace
                        && orientation == GateOrientation.WIDTH_Z
                        && part.getRelativeX() != faceCoordinate) {
                    continue;
                }

                int coordinate = orientation == GateOrientation.WIDTH_X
                        ? part.getRelativeX()
                        : part.getRelativeZ();
                if (isAttackLaneFailed(coordinate)) {
                    continue;
                }

                int failedLaneSeparation =
                        getMinimumFailedLaneSeparation(coordinate);
                if (selectingAlternate
                        && failedLaneSeparation
                        < ATTACK_ALTERNATE_LANE_MIN_SEPARATION) {
                    continue;
                }

                double centerDistance = Math.abs(
                        coordinate + 0.5D - centerCoordinate
                );

                boolean betterCandidate;
                if (bestCoordinate == NO_ATTACK_LANE) {
                    betterCandidate = true;
                } else if (selectingAlternate
                        && failedLaneSeparation
                        != bestFailedLaneSeparation) {
                    betterCandidate = failedLaneSeparation
                            > bestFailedLaneSeparation;
                } else if (Math.abs(
                        centerDistance - bestCenterDistance
                ) >= 1.0E-9D) {
                    betterCandidate = centerDistance
                            < bestCenterDistance;
                } else {
                    betterCandidate = coordinate < bestCoordinate;
                }

                if (betterCandidate) {
                    bestFailedLaneSeparation = failedLaneSeparation;
                    bestCenterDistance = centerDistance;
                    bestCoordinate = coordinate;
                }
            }

            if (bestCoordinate != NO_ATTACK_LANE) {
                return bestCoordinate;
            }
        }

        return NO_ATTACK_LANE;
    }

    private int getMinimumFailedLaneSeparation(int coordinate) {
        if (failedAttackLaneCoordinates.isEmpty()) {
            return Integer.MAX_VALUE;
        }

        int minimumSeparation = Integer.MAX_VALUE;
        for (Integer failedCoordinate : failedAttackLaneCoordinates) {
            if (failedCoordinate == null) {
                continue;
            }
            minimumSeparation = Math.min(
                    minimumSeparation,
                    Math.abs(coordinate - failedCoordinate.intValue())
            );
        }
        return minimumSeparation;
    }

    private AttackPoint createAttackPointForLane(
            TileEntitySiegeGate gate,
            List<GatePartData> parts,
            GateOrientation orientation,
            int minX,
            int maxX,
            int minZ,
            int maxZ,
            int minY,
            int sideSign,
            int laneCoordinate
    ) {
        int faceCoordinate = orientation == GateOrientation.WIDTH_X
                ? (sideSign < 0 ? minZ : maxZ)
                : (sideSign < 0 ? minX : maxX);

        GatePartData impactPart = null;
        for (GatePartData part : parts) {
            if (part.getRelativeY() != minY) {
                continue;
            }
            if (orientation == GateOrientation.WIDTH_X) {
                if (part.getRelativeZ() != faceCoordinate
                        || part.getRelativeX() != laneCoordinate) {
                    continue;
                }
            } else if (part.getRelativeX() != faceCoordinate
                    || part.getRelativeZ() != laneCoordinate) {
                continue;
            }
            impactPart = part;
            break;
        }

        if (impactPart == null) {
            for (GatePartData part : parts) {
                if (part.getRelativeY() != minY) {
                    continue;
                }
                int coordinate = orientation == GateOrientation.WIDTH_X
                        ? part.getRelativeX()
                        : part.getRelativeZ();
                if (coordinate == laneCoordinate) {
                    impactPart = part;
                    break;
                }
            }
        }

        if (impactPart == null) {
            return null;
        }

        double normalX = orientation == GateOrientation.WIDTH_Z
                ? sideSign
                : 0.0D;
        double normalZ = orientation == GateOrientation.WIDTH_X
                ? sideSign
                : 0.0D;

        double impactX;
        double impactZ;

        if (orientation == GateOrientation.WIDTH_X) {
            impactX = gate.xCoord + laneCoordinate + 0.5D;
            impactZ = sideSign < 0
                    ? gate.zCoord + minZ
                    : gate.zCoord + maxZ + 1.0D;
        } else {
            impactX = sideSign < 0
                    ? gate.xCoord + minX
                    : gate.xCoord + maxX + 1.0D;
            impactZ = gate.zCoord + laneCoordinate + 0.5D;
        }

        double approachY = gate.yCoord + minY;
        double impactY = approachY + 0.5D;

        return new AttackPoint(
                impactX + normalX * ATTACK_STANDOFF_DISTANCE,
                approachY,
                impactZ + normalZ * ATTACK_STANDOFF_DISTANCE,
                impactX,
                impactY,
                impactZ,
                impactPart
        );
    }

    private boolean isAttackLaneFailed(int coordinate) {
        return failedAttackLaneCoordinates.contains(
                Integer.valueOf(coordinate)
        );
    }

    private void failCurrentAttackLane() {
        if (attackLaneCoordinate != NO_ATTACK_LANE
                && !isAttackLaneFailed(attackLaneCoordinate)) {
            failedAttackLaneCoordinates.add(
                    Integer.valueOf(attackLaneCoordinate)
            );
        }
        attackLaneCoordinate = NO_ATTACK_LANE;
        attackAlignmentFailureTicks = 0;
    }

    private void resetAttackLaneSelectionForFace() {
        attackLaneCoordinate = NO_ATTACK_LANE;
        failedAttackLaneCoordinates.clear();
        attackAlignmentFailureTicks = 0;
    }

    private void resetPathProgressForNewLane() {
        pathFailureTicks = 0;
        pathProgressCheckTicks = 0;
        pathRequestCooldownTicks = 0;
        lastPathDistance = Double.MAX_VALUE;
        lastPathProgressX = Double.NaN;
        lastPathProgressZ = Double.NaN;
        lastPathRequestSucceeded = false;
        pathRequestFailedSinceProgressCheck = false;
        consecutivePathRequestFailures = 0;
    }

    private boolean alignExactlyForAttack(AttackPoint point) {
        if (point == null) {
            return false;
        }

        faceImpactPoint(point);

        if (Math.abs(posY - point.approachY)
                > ATTACK_POSITION_VERTICAL_TOLERANCE) {
            return false;
        }

        double dx = point.approachX - posX;
        double dz = point.approachZ - posZ;
        double distance = Math.sqrt(dx * dx + dz * dz);

        if (distance <= ATTACK_ALIGNMENT_POSITION_TOLERANCE) {
            motionX = 0.0D;
            motionZ = 0.0D;
            attackAlignmentFailureTicks = 0;
            return true;
        }

        double step = Math.min(
                ATTACK_ALIGNMENT_STEP,
                distance
        );

        moveAttackHorizontally(
                dx / distance * step,
                dz / distance * step
        );

        motionX = 0.0D;
        motionZ = 0.0D;
        faceImpactPoint(point);

        double remainingX = point.approachX - posX;
        double remainingZ = point.approachZ - posZ;
        double remaining = Math.sqrt(
                remainingX * remainingX
                        + remainingZ * remainingZ
        );

        /*
         * Final alignment deliberately bypasses PathNavigate. Count repeated
         * collision/sliding failures here so a blocked endpoint cannot trap
         * the ram forever inside this branch.
         */
        double progress = distance - remaining;
        if (progress < ATTACK_ALIGNMENT_MIN_PROGRESS
                && remaining > ATTACK_ALIGNMENT_POSITION_TOLERANCE) {
            ++attackAlignmentFailureTicks;
            if (attackAlignmentFailureTicks
                    >= ATTACK_ALIGNMENT_FAILURE_TICKS) {
                failCurrentAttackLane();
                prepareForGateRepath();
            }
            return false;
        }

        attackAlignmentFailureTicks = 0;
        return remaining <= ATTACK_ALIGNMENT_POSITION_TOLERANCE;
    }

    private boolean isStrictlyAlignedForAttack(AttackPoint point) {
        return point != null
                && getHorizontalApproachDistance(point)
                <= ATTACK_ALIGNMENT_POSITION_TOLERANCE
                && Math.abs(posY - point.approachY)
                <= ATTACK_POSITION_VERTICAL_TOLERANCE;
    }

    private void faceImpactPoint(AttackPoint point) {
        double dx = point.impactX - posX;
        double dz = point.impactZ - posZ;
        float yaw = (float)(Math.atan2(dz, dx) * 180.0D / Math.PI)
                - 90.0F;
        rotationYaw = yaw;
        synchronizeRamFormationHeading();
    }

    private void synchronizeRamFormationHeading() {
        renderYawOffset = rotationYaw;
        rotationYawHead = rotationYaw;
        prevRenderYawOffset = prevRotationYaw;
        prevRotationYawHead = prevRotationYaw;
    }

    private TileEntitySiegeGate getLoadedTargetGate() {
        if (!hasGateTarget()
                || worldObj == null
                || worldObj.provider.dimensionId != targetDimension
                || !worldObj.blockExists(
                getTargetControllerX(),
                getTargetControllerY(),
                getTargetControllerZ()
        )) {
            return null;
        }
        net.minecraft.tileentity.TileEntity tileEntity =
                worldObj.getTileEntity(
                        getTargetControllerX(),
                        getTargetControllerY(),
                        getTargetControllerZ()
                );
        return tileEntity instanceof TileEntitySiegeGate
                ? (TileEntitySiegeGate)tileEntity
                : null;
    }

    private void finishBreachedTarget(TileEntitySiegeGate gate) {
        if (gate != null) {
            gate.clearRamReservation(getUniqueID());
        }

        if (!targetQueue.isEmpty()) {
            targetQueue.remove(0);
        }

        clearActiveTargetData();

        if (targetQueue.isEmpty()) {
            pauseRamWithoutResumeTarget();
        } else {
            activateTargetQueueHead();
        }

        RamControlManager.syncTargetQueueToCommander(this);
        sendCommanderBreachMessage(!targetQueue.isEmpty());
    }

    private void abandonTarget(String message) {
        if (!targetQueue.isEmpty()) {
            QueuedGateTarget abandoned = targetQueue.remove(0);
            clearReservationForEntry(abandoned);
        } else {
            TileEntitySiegeGate gate = getLoadedTargetGate();
            if (gate != null) {
                gate.clearRamReservation(getUniqueID());
            }
        }

        clearActiveTargetData();

        if (targetQueue.isEmpty()) {
            pauseRamWithoutResumeTarget();
            sendCommanderMessage(message + " Battle Ram paused.");
        } else {
            activateTargetQueueHead();
            sendCommanderMessage(message + " Advancing to the next queued gate.");
        }
        RamControlManager.syncTargetQueueToCommander(this);
    }

    private void releaseGateReservation() {
        if (!targetQueue.isEmpty()) {
            for (QueuedGateTarget queued :
                    new ArrayList<QueuedGateTarget>(targetQueue)) {
                clearReservationForEntry(queued);
            }
            return;
        }

        TileEntitySiegeGate gate = getLoadedTargetGate();
        if (gate != null) {
            gate.clearRamReservation(getUniqueID());
        }
    }

    private void clearTargetData() {
        targetQueue.clear();
        clearActiveTargetData();
    }

    private void clearActiveTargetData() {
        targetDimension = 0;
        targetGateUuid = null;
        dataWatcher.updateObject(WATCHER_HAS_TARGET, Byte.valueOf((byte)0));
        dataWatcher.updateObject(WATCHER_TARGET_X, Integer.valueOf(0));
        dataWatcher.updateObject(WATCHER_TARGET_Y, Integer.valueOf(0));
        dataWatcher.updateObject(WATCHER_TARGET_Z, Integer.valueOf(0));
        resetPhysicalAttackRun();
        resetTargetProgress();
    }

    private void activateTargetQueueHead() {
        if (targetQueue.isEmpty()) {
            clearActiveTargetData();
            pauseRamWithoutResumeTarget();
            return;
        }

        QueuedGateTarget target = targetQueue.get(0);
        targetDimension = target.dimensionId;
        targetGateUuid = target.gateUuid;
        dataWatcher.updateObject(WATCHER_HAS_TARGET, Byte.valueOf((byte)1));
        dataWatcher.updateObject(WATCHER_TARGET_X, target.controllerX);
        dataWatcher.updateObject(WATCHER_TARGET_Y, target.controllerY);
        dataWatcher.updateObject(WATCHER_TARGET_Z, target.controllerZ);
        resetTargetProgress();
        setRamState(BattleRamState.MOVE_TO_GATE);
    }

    private boolean activeTargetMatches(QueuedGateTarget target) {
        return target != null
                && hasGateTarget()
                && target.dimensionId == targetDimension
                && target.controllerX == getTargetControllerX()
                && target.controllerY == getTargetControllerY()
                && target.controllerZ == getTargetControllerZ();
    }

    private boolean isQueuedCoordinateLoaded(QueuedGateTarget target) {
        return target != null
                && worldObj != null
                && target.dimensionId == worldObj.provider.dimensionId
                && worldObj.blockExists(
                target.controllerX,
                target.controllerY,
                target.controllerZ
        );
    }

    private TileEntitySiegeGate getLoadedQueuedGate(QueuedGateTarget target) {
        if (!isQueuedCoordinateLoaded(target)) {
            return null;
        }

        net.minecraft.tileentity.TileEntity tileEntity =
                worldObj.getTileEntity(
                        target.controllerX,
                        target.controllerY,
                        target.controllerZ
                );

        return tileEntity instanceof TileEntitySiegeGate
                ? (TileEntitySiegeGate)tileEntity
                : null;
    }

    private void clearReservationForEntry(QueuedGateTarget target) {
        TileEntitySiegeGate gate = getLoadedQueuedGate(target);
        if (gate != null) {
            gate.clearRamReservation(getUniqueID());
        }
    }

    private void sendCommanderBreachMessage(boolean hasNextTarget) {
        EntityPlayer commander = getCommander();
        if (commander == null || worldObj == null) {
            return;
        }

        ChatComponentText message = new ChatComponentText(
                hasNextTarget
                        ? "Your ram unit successfully breached its targeted gate and is advancing to its next target. "
                        : "Your ram unit successfully breached its targeted gate. "
        );

        ChatComponentText editLink = new ChatComponentText(
                hasNextTarget
                        ? "[Edit future targets]"
                        : "[Assign new targets]"
        );

        editLink.setChatStyle(new ChatStyle()
                .setColor(EnumChatFormatting.GREEN)
                .setUnderlined(true)
                .setChatClickEvent(new ClickEvent(
                        ClickEvent.Action.RUN_COMMAND,
                        "/ramtargets "
                                + worldObj.provider.dimensionId
                                + " "
                                + getEntityId()
                )));

        message.appendSibling(editLink);
        commander.addChatMessage(message);
    }

    private void pauseRamWithoutResumeTarget() {
        /*
         * Empty target queues are a deliberate idle state. Clear every stale
         * gate-navigation/attack value before pausing so the next manual
         * resume always behaves as a fresh FOLLOW_COMMANDER transition.
         */
        resumeState = BattleRamState.FOLLOW_COMMANDER;
        getNavigator().clearPathEntity();
        resetPhysicalAttackRun();
        resetTargetProgress();
        motionX = 0.0D;
        motionZ = 0.0D;
        setRamState(BattleRamState.PAUSED);
    }

    private void resetTargetProgress() {
        resetPathProgressForNewLane();
        triedOppositeSide = false;
        attackSideSign = 0;
        resetAttackLaneSelectionForFace();
    }

    private void resetNoProgressWhileWaitingForCrew() {
        pathFailureTicks = 0;
        pathProgressCheckTicks = 0;
        pathRequestCooldownTicks = 0;
        lastPathDistance = Double.MAX_VALUE;
        lastPathProgressX = Double.NaN;
        lastPathProgressZ = Double.NaN;
        lastPathRequestSucceeded = false;
        pathRequestFailedSinceProgressCheck = false;
        consecutivePathRequestFailures = 0;
    }

    private void prepareForGateRepath() {
        pathProgressCheckTicks = 0;
        pathRequestCooldownTicks = 0;
        lastPathDistance = Double.MAX_VALUE;
        lastPathProgressX = Double.NaN;
        lastPathProgressZ = Double.NaN;
        lastPathRequestSucceeded = false;
        pathRequestFailedSinceProgressCheck = false;
        consecutivePathRequestFailures = 0;
        getNavigator().clearPathEntity();
    }

    private void sendCommanderMessage(String message) {
        EntityPlayer commander = getCommander();
        if (commander != null) {
            commander.addChatMessage(new ChatComponentText(message));
        }
    }

    public EntityPlayer getCommander() {
        return commanderUuid == null
                ? null
                : worldObj.func_152378_a(commanderUuid);
    }

    public boolean isCommanderOrAdministrator(EntityPlayerMP player) {
        return player != null
                && (player.getUniqueID().equals(commanderUuid)
                || GateAccess.isAdministrativePlayer(player));
    }

    public void pauseRam() {
        BattleRamState current = getRamState();
        if (current != BattleRamState.PAUSED) {
            resumeState = current;
            setRamState(BattleRamState.PAUSED);
        }
    }

    public void resumeRam() {
        if (getRamState() != BattleRamState.PAUSED) {
            return;
        }

        /*
         * A final breached target deliberately pauses the ram with no active
         * gate. Re-enter FOLLOW_COMMANDER as a fresh movement state instead
         * of reusing stale gate-path bookkeeping from the completed attack.
         */
        if (!hasGateTarget()) {
            resumeState = BattleRamState.FOLLOW_COMMANDER;
            getNavigator().clearPathEntity();
            resetTargetProgress();
            pathRequestCooldownTicks = 0;
            lastPathRequestSucceeded = false;
            setRamState(BattleRamState.FOLLOW_COMMANDER);
            updateCommanderFollow();
            return;
        }

        BattleRamState state = resumeState;
        if (state == null
                || state == BattleRamState.PAUSED
                || state == BattleRamState.FOLLOW_COMMANDER) {
            state = BattleRamState.MOVE_TO_GATE;
        }
        setRamState(state);
    }

    @Override
    public boolean interact(EntityPlayer player) {
        if (worldObj.isRemote) {
            return true;
        }
        if (!(player instanceof EntityPlayerMP)) {
            return false;
        }
        EntityPlayerMP serverPlayer = (EntityPlayerMP)player;
        if (commanderUuid == null) {
            commanderUuid = player.getUniqueID();
        }
        if (!isCommanderOrAdministrator(serverPlayer)) {
            player.addChatMessage(new ChatComponentText(
                    "Only this Battle Ram's commander may control it."
            ));
            return true;
        }
        if (player.isSneaking()) {
            RamControlManager.open(serverPlayer, this);
        } else if (getRamState() == BattleRamState.PAUSED) {
            resumeRam();
            player.addChatMessage(new ChatComponentText(
                    "Battle Ram resumed."
            ));
        } else {
            pauseRam();
            player.addChatMessage(new ChatComponentText(
                    "Battle Ram paused."
            ));
        }
        return true;
    }

    public boolean disband(EntityPlayerMP player) {
        if (!isCommanderOrAdministrator(player) || deliberateDisband) {
            return false;
        }
        if (!ensureDurableOwnership()) {
            return false;
        }
        SiegeRamCrewOwnershipData data = SiegeRamCrewOwnershipData.get(
                worldObj, false
        );
        if (data == null || !data.retireRam(
                worldObj, getUniqueID(), ramGeneration,
                SiegeRamCrewOwnershipData.RamStatus.DISBANDED_TOMBSTONE
        )) {
            return false;
        }
        deliberateDisband = true;
        getNavigator().clearPathEntity();
        releaseGateReservation();
        clearTargetData();
        removeAssociatedCrew();
        setDead();
        return true;
    }

    /**
     * Guarded direct-removal interception. It only runs for an entity still in
     * a loaded server chunk; world unload is explicitly marked by the handler.
     */
    @Override
    public void setDead() {
        if (!isDead && worldObj != null && !worldObj.isRemote
                && !deliberateDisband && !worldUnloading && addedToChunk
                && ensureDurableOwnership()) {
            SiegeRamCrewOwnershipData data = SiegeRamCrewOwnershipData.get(
                    worldObj, false
            );
            if (data != null && data.retireRam(
                    worldObj, getUniqueID(), ramGeneration,
                    SiegeRamCrewOwnershipData.RamStatus
                            .ABNORMALLY_REMOVED_TOMBSTONE
            )) {
                deliberateDisband = true;
                releaseGateReservation();
                clearTargetData();
                removeAssociatedCrew();
            }
        }
        super.setDead();
    }

    public void markWorldUnloading() {
        worldUnloading = true;
    }

    protected void removeAssociatedCrew() {
        if (worldObj != null && !worldObj.isRemote && crewConfigured) {
            reassociateLoadedCrew();
        }
        for (int slot = 0; slot < CREW_SLOT_COUNT; ++slot) {
            LOTREntityNPC crew = crewReferences[slot];
            if (crew != null && !crew.isDead) {
                SiegeNetwork.syncRamCrewAttachment(
                        this,
                        crew,
                        slot,
                        false
                );
                crew.setDead();
            }
            crewReferences[slot] = null;
            crewEntityUuids[slot] = null;
            crewSlotAlive[slot] = false;
            crewRespawnAt[slot] = 0L;
        }
        crewConfigured = false;
    }

    @Override
    public boolean attackEntityFrom(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean canBePushed() {
        return false;
    }

    /**
     * Projectiles in 1.7.10 use entity collidability while tracing their
     * flight. The ram is scenery/siege equipment, not a projectile shield.
     * Direct player control is preserved by the client-side manual ram
     * interaction ray trace.
     */
    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public void applyEntityCollision(net.minecraft.entity.Entity entity) {
        // The ram neither pushes nor is pushed by players, mobs, or carriers.
    }

    @Override
    public boolean canAttackClass(Class entityClass) {
        return false;
    }

    @Override
    public boolean attackEntityAsMob(net.minecraft.entity.Entity entity) {
        return false;
    }

    @Override
    public boolean isEntityInvulnerable() {
        return true;
    }

    @Override
    protected boolean canDespawn() {
        return false;
    }

    @Override
    protected void fall(float distance) {
    }

    @Override
    public void writeEntityToNBT(NBTTagCompound nbt) {
        super.writeEntityToNBT(nbt);
        if (commanderUuid != null) {
            nbt.setString(NBT_COMMANDER_UUID, commanderUuid.toString());
        }
        LOTRFaction faction = getRamFaction();
        if (faction != null) {
            nbt.setString(NBT_FACTION, faction.codeName());
        }
        nbt.setString(NBT_STATE, getRamState().name());
        nbt.setString(NBT_RESUME_STATE, resumeState.name());
        nbt.setBoolean(NBT_CREW_CONFIGURED, crewConfigured);
        NBTTagList crewSlots = new NBTTagList();
        for (int slot = 0; slot < CREW_SLOT_COUNT; ++slot) {
            NBTTagCompound slotNbt = new NBTTagCompound();
            slotNbt.setInteger(NBT_CREW_SLOT, slot);
            slotNbt.setBoolean(NBT_CREW_ALIVE, crewSlotAlive[slot]);
            if (crewEntityUuids[slot] != null) {
                slotNbt.setString(
                        NBT_CREW_UUID,
                        crewEntityUuids[slot].toString()
                );
            }
            slotNbt.setLong(NBT_CREW_RESPAWN_AT, crewRespawnAt[slot]);
            crewSlots.appendTag(slotNbt);
        }
        nbt.setTag(NBT_CREW_SLOTS, crewSlots);
        if (hasGateTarget()) {
            nbt.setInteger(NBT_TARGET_DIMENSION, targetDimension);
            nbt.setInteger(NBT_TARGET_X, getTargetControllerX());
            nbt.setInteger(NBT_TARGET_Y, getTargetControllerY());
            nbt.setInteger(NBT_TARGET_Z, getTargetControllerZ());
            if (targetGateUuid != null) {
                nbt.setString(
                        NBT_TARGET_GATE_UUID,
                        targetGateUuid.toString()
                );
            }
        }

        NBTTagList targetQueueNbt = new NBTTagList();
        for (QueuedGateTarget target : targetQueue) {
            NBTTagCompound targetNbt = new NBTTagCompound();
            targetNbt.setInteger(NBT_QUEUE_DIMENSION, target.dimensionId);
            targetNbt.setInteger(NBT_QUEUE_X, target.controllerX);
            targetNbt.setInteger(NBT_QUEUE_Y, target.controllerY);
            targetNbt.setInteger(NBT_QUEUE_Z, target.controllerZ);
            if (target.gateUuid != null) {
                targetNbt.setString(
                        NBT_QUEUE_GATE_UUID,
                        target.gateUuid.toString()
                );
            }
            targetQueueNbt.appendTag(targetNbt);
        }
        nbt.setTag(NBT_TARGET_QUEUE, targetQueueNbt);

        nbt.setInteger(NBT_ATTACK_TICKS, attackTicks);
        nbt.setFloat(
                NBT_ATTACK_PROGRESS_REMAINDER,
                attackProgressRemainder
        );
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound nbt) {
        super.readEntityFromNBT(nbt);
        commanderUuid = readUuid(nbt.getString(NBT_COMMANDER_UUID));
        setRamFaction(LOTRFaction.forName(nbt.getString(NBT_FACTION)));
        setRamState(BattleRamState.fromName(nbt.getString(NBT_STATE)));
        resumeState = BattleRamState.fromName(
                nbt.getString(NBT_RESUME_STATE)
        );
        crewConfigured = nbt.getBoolean(NBT_CREW_CONFIGURED)
                && BattleRamCrewTypes.isSupported(getRamFaction());
        for (int slot = 0; slot < CREW_SLOT_COUNT; ++slot) {
            crewSlotAlive[slot] = false;
            crewEntityUuids[slot] = null;
            crewRespawnAt[slot] = 0L;
            crewReferences[slot] = null;
        }
        NBTTagList slots = nbt.getTagList(NBT_CREW_SLOTS, 10);
        for (int i = 0; i < slots.tagCount(); ++i) {
            NBTTagCompound slotNbt = slots.getCompoundTagAt(i);
            int slot = slotNbt.getInteger(NBT_CREW_SLOT);
            if (slot < 0 || slot >= CREW_SLOT_COUNT) {
                continue;
            }
            crewSlotAlive[slot] = slotNbt.getBoolean(NBT_CREW_ALIVE);
            crewEntityUuids[slot] = readUuid(
                    slotNbt.getString(NBT_CREW_UUID)
            );
            crewRespawnAt[slot] = Math.max(
                    0L,
                    slotNbt.getLong(NBT_CREW_RESPAWN_AT)
            );
        }
        targetQueue.clear();

        if (nbt.hasKey(NBT_TARGET_QUEUE)) {
            NBTTagList queuedTargets = nbt.getTagList(NBT_TARGET_QUEUE, 10);
            for (int i = 0;
                 i < queuedTargets.tagCount()
                         && targetQueue.size() < MAX_TARGET_QUEUE_SIZE;
                 ++i) {
                NBTTagCompound targetNbt =
                        queuedTargets.getCompoundTagAt(i);
                int dimensionId = targetNbt.getInteger(NBT_QUEUE_DIMENSION);
                int controllerX = targetNbt.getInteger(NBT_QUEUE_X);
                int controllerY = targetNbt.getInteger(NBT_QUEUE_Y);
                int controllerZ = targetNbt.getInteger(NBT_QUEUE_Z);
                UUID gateUuid = readUuid(
                        targetNbt.getString(NBT_QUEUE_GATE_UUID)
                );

                if (worldObj != null
                        && dimensionId != worldObj.provider.dimensionId) {
                    continue;
                }

                targetQueue.add(new QueuedGateTarget(
                        dimensionId,
                        controllerX,
                        controllerY,
                        controllerZ,
                        gateUuid
                ));
            }
        }

        if (targetQueue.isEmpty()
                && nbt.hasKey(NBT_TARGET_DIMENSION)
                && nbt.hasKey(NBT_TARGET_X)
                && nbt.hasKey(NBT_TARGET_Y)
                && nbt.hasKey(NBT_TARGET_Z)) {
            targetQueue.add(new QueuedGateTarget(
                    nbt.getInteger(NBT_TARGET_DIMENSION),
                    nbt.getInteger(NBT_TARGET_X),
                    nbt.getInteger(NBT_TARGET_Y),
                    nbt.getInteger(NBT_TARGET_Z),
                    readUuid(nbt.getString(NBT_TARGET_GATE_UUID))
            ));
        }

        if (!targetQueue.isEmpty()) {
            QueuedGateTarget head = targetQueue.get(0);
            targetDimension = head.dimensionId;
            targetGateUuid = head.gateUuid;
            dataWatcher.updateObject(
                    WATCHER_HAS_TARGET,
                    Byte.valueOf((byte)1)
            );
            dataWatcher.updateObject(WATCHER_TARGET_X, head.controllerX);
            dataWatcher.updateObject(WATCHER_TARGET_Y, head.controllerY);
            dataWatcher.updateObject(WATCHER_TARGET_Z, head.controllerZ);
        } else {
            clearActiveTargetData();
        }
        attackTicks = Math.max(
                0,
                Math.min(
                        nbt.getInteger(NBT_ATTACK_TICKS),
                        ATTACK_INTERVAL_TICKS - 1
                )
        );

        attackProgressRemainder =
                nbt.hasKey(NBT_ATTACK_PROGRESS_REMAINDER)
                        ? Math.max(
                        0.0F,
                        Math.min(
                                nbt.getFloat(
                                        NBT_ATTACK_PROGRESS_REMAINDER
                                ),
                                0.999F
                        )
                )
                        : 0.0F;
        resetPhysicalAttackRun();
        if (getRamState() == BattleRamState.ATTACK_GATE) {
            /*
             * A saved physical run may have been halfway between its retreat
             * and contact points. Reacquire the gate cleanly after load rather
             * than snapping into an unknown sub-phase.
             */
            setRamState(BattleRamState.MOVE_TO_GATE);
        }
        resetTargetProgress();
        ramGeneration = 0L;
        durableOwnershipReady = false;
    }

    /**
     * Called on server load/join before crew lifecycle work. Existing rams are
     * imported exactly once; later loads use the WorldSavedData authority.
     */
    public boolean ensureDurableOwnership() {
        if (worldObj == null || worldObj.isRemote) {
            return false;
        }
        LOTRFaction faction = getRamFaction();
        if (!BattleRamCrewTypes.isSupported(faction)) {
            return false;
        }
        SiegeRamCrewOwnershipData data = SiegeRamCrewOwnershipData.get(
                worldObj, true
        );
        if (data == null || data.isReadOnlyDueToInvalidData()) {
            return false;
        }
        SiegeRamCrewOwnershipData.RamSnapshot record = data.getRam(
                getUniqueID()
        );
        if (record == null) {
            SiegeRamCrewOwnershipData.LegacySlot[] legacy =
                    new SiegeRamCrewOwnershipData.LegacySlot[CREW_SLOT_COUNT];
            for (int slot = 0; slot < CREW_SLOT_COUNT; ++slot) {
                legacy[slot] = new SiegeRamCrewOwnershipData.LegacySlot(
                        crewSlotAlive[slot], crewEntityUuids[slot],
                        crewRespawnAt[slot]
                );
            }
            record = data.migrateLegacyRam(
                    worldObj, getUniqueID(), faction, legacy,
                    ((int)Math.floor(posX)) >> 4,
                    ((int)Math.floor(posZ)) >> 4
            );
        }
        if (record == null || record.status
                != SiegeRamCrewOwnershipData.RamStatus.ACTIVE) {
            return false;
        }
        if (LOTRFaction.forName(record.factionCode) != faction) {
            data.quarantineRam(worldObj, getUniqueID(), record.generation,
                    "RAM_FACTION_MISMATCH");
            return false;
        }
        ramGeneration = record.generation;
        durableOwnershipReady = true;
        data.touchRam(
                worldObj, getUniqueID(), ramGeneration,
                ((int)Math.floor(posX)) >> 4, ((int)Math.floor(posZ)) >> 4
        );
        for (int slot = 0; slot < CREW_SLOT_COUNT; ++slot) {
            SiegeRamCrewOwnershipData.SlotSnapshot value = record.getSlot(slot);
            if (value != null && value.state
                    == SiegeRamCrewOwnershipData.SlotState.SPAWN_PREPARED) {
                data.enqueue(new SiegeRamCrewOwnershipData.RamSlotKey(
                        getUniqueID(), slot
                ));
            }
        }
        return true;
    }

    public long getRamGeneration() {
        return ramGeneration;
    }

    public boolean hasDurableOwnership() {
        return durableOwnershipReady && ramGeneration > 0L;
    }

    private void initializeCrewSlots() {
        crewConfigured = BattleRamCrewTypes.isSupported(getRamFaction());
        long worldTick = worldObj == null
                ? 0L
                : worldObj.getTotalWorldTime();
        for (int slot = 0; slot < CREW_SLOT_COUNT; ++slot) {
            crewReferences[slot] = null;
            crewEntityUuids[slot] = null;
            crewSlotAlive[slot] = false;
            crewRespawnAt[slot] = worldTick;
        }
    }

    private void updateCrewSlots() {
        if (!crewConfigured || !hasDurableOwnership()) {
            return;
        }
        /* This is only a bounded local arrival optimization. EntityJoinWorld
         * remains the authoritative path and this window never changes slot
         * state when a carrier is absent. */
        if (ticksExisted <= CREW_REASSOCIATION_GRACE_TICKS
                && ticksExisted % CREW_REASSOCIATION_INTERVAL_TICKS == 1) {
            reassociateLoadedCrew();
        }
        synchronizeCrewSlotsFromDurableData();
        long worldTick = worldObj.getTotalWorldTime();
        for (int slot = 0; slot < CREW_SLOT_COUNT; ++slot) {
            LOTREntityNPC crew = crewReferences[slot];
            if (crew != null && (crew.isDead || crew.getHealth() <= 0.0F)) {
                /* LivingDeathEvent is the only authority that can transition a
                 * durable current carrier into a respawn. A dead reference may
                 * be stale after that event, unloaded, or direct setDead. */
                crewReferences[slot] = null;
                crew = null;
            }
            SiegeRamCrewOwnershipData.SlotSnapshot durableSlot = getDurableSlot(
                    slot
            );
            if (durableSlot != null
                    && durableSlot.state
                    == SiegeRamCrewOwnershipData.SlotState.DEAD_RESPAWN_PENDING
                    && worldTick >= durableSlot.respawnAt) {
                SiegeRamDiagnostics.serverOnce(
                        worldObj.provider.dimensionId,
                        "respawn-due:" + getUniqueID() + ":" + ramGeneration
                                + ":" + slot + ":" + durableSlot.respawnAt,
                        "RESPAWN_DUE",
                        "ram=" + getUniqueID() + " gen=" + ramGeneration
                                + " slot=" + slot + " state="
                                + durableSlot.state + " now=" + worldTick
                                + " respawnAt=" + durableSlot.respawnAt
                                + " dimension="
                                + worldObj.provider.dimensionId
                                + " ramAdded=" + addedToChunk
                );
                spawnCrewForSlot(slot, worldTick);
                crew = crewReferences[slot];
            }
            if (crew != null && !crew.isDead) {
                positionCrew(crew, slot);
            }
        }
        dataWatcher.updateObject(
                WATCHER_LIVING_CREW,
                Integer.valueOf(getLivingCrewCount())
        );
    }

    private SiegeRamCrewOwnershipData.SlotSnapshot getDurableSlot(int slot) {
        SiegeRamCrewOwnershipData data = SiegeRamCrewOwnershipData.get(
                worldObj, false
        );
        return data == null ? null : data.getSlot(
                getUniqueID(), ramGeneration, slot
        );
    }

    private void synchronizeCrewSlotsFromDurableData() {
        SiegeRamCrewOwnershipData data = SiegeRamCrewOwnershipData.get(
                worldObj, false
        );
        if (data == null) {
            return;
        }
        for (int slot = 0; slot < CREW_SLOT_COUNT; ++slot) {
            SiegeRamCrewOwnershipData.SlotSnapshot value = data.getSlot(
                    getUniqueID(), ramGeneration, slot
            );
            if (value == null) {
                continue;
            }
            boolean expected = value.state
                    == SiegeRamCrewOwnershipData.SlotState.ALIVE_EXPECTED
                    || value.state
                    == SiegeRamCrewOwnershipData.SlotState.SPAWN_PREPARED;
            crewSlotAlive[slot] = expected;
            crewEntityUuids[slot] = value.expectedCrewUuid;
            crewRespawnAt[slot] = value.respawnAt;
            if (crewReferences[slot] != null
                    && (value.expectedCrewUuid == null
                    || !value.expectedCrewUuid.equals(
                    crewReferences[slot].getUniqueID()))) {
                crewReferences[slot] = null;
            }
        }
    }

    private void reassociateLoadedCrew() {
        AxisAlignedBB searchBounds = boundingBox.expand(
                CREW_REASSOCIATION_RANGE,
                CREW_REASSOCIATION_RANGE,
                CREW_REASSOCIATION_RANGE
        );
        List loadedCrew = worldObj.getEntitiesWithinAABB(
                LOTREntityNPC.class,
                searchBounds
        );
        String ramUuid = getUniqueID().toString();
        for (Object object : loadedCrew) {
            LOTREntityNPC crew = (LOTREntityNPC)object;
            NBTTagCompound entityData = crew.getEntityData();
            if (!ramUuid.equals(entityData.getString(CREW_RAM_UUID))) {
                continue;
            }
            int slot = entityData.getInteger(CREW_SLOT);
            if (slot >= 0 && slot < CREW_SLOT_COUNT) {
                attachLoadedCrewIfValid(crew);
            }
        }
    }

    private void spawnCrewForSlot(int slot, long worldTick) {
        Class<? extends LOTREntityNPC> crewClass =
                BattleRamCrewTypes.getCrewClass(getRamFaction());
        SiegeRamCrewOwnershipData.SlotSnapshot diagnosticSlot =
                getDurableSlot(slot);
        long diagnosticDeadline = diagnosticSlot == null
                ? -1L : diagnosticSlot.respawnAt;
        String diagnosticFields = "ram=" + getUniqueID() + " gen="
                + ramGeneration + " slot=" + slot + " faction="
                + getRamFaction() + " mappedClass="
                + (crewClass == null ? "null" : crewClass.getName())
                + " now=" + worldTick;
        SiegeRamDiagnostics.serverOnce(
                worldObj.provider.dimensionId,
                "spawn-attempt:" + getUniqueID() + ":" + ramGeneration
                        + ":" + slot + ":" + diagnosticDeadline,
                "SPAWN_ATTEMPT", diagnosticFields
        );
        if (crewClass == null) {
            crewConfigured = false;
            return;
        }
        LOTREntityNPC crew;
        try {
            Constructor<? extends LOTREntityNPC> constructor =
                    crewClass.getConstructor(World.class);
            crew = constructor.newInstance(worldObj);
        } catch (Exception exception) {
            SiegeRamDiagnostics.serverOnce(
                    worldObj.provider.dimensionId,
                    "spawn-construct-fail:" + getUniqueID() + ":"
                            + ramGeneration + ":" + slot,
                    "SPAWN_CONSTRUCT_FAIL",
                    diagnosticFields + " exception="
                            + exception.getClass().getName() + " message="
                            + conciseMessage(exception.getMessage())
            );
            return;
        }

        positionCrew(crew, slot);
        /* LOTR's own initCreatureForHire uses this native flag to suppress
         * faction-specific horse/elk/zebra/rhino creation. Ram crew are
         * always the on-foot form of their faction class. */
        crew.spawnRidingHorse = false;
        crew.onSpawnWithEgg((IEntityLivingData)null);
        NBTTagCompound entityData = crew.getEntityData();
        tagCrewForRam(crew, getUniqueID(), ramGeneration, slot);
        prepareAttachedCrew(crew);
        if (!BattleRamCrewTypes.isApprovedGroundCrew(getRamFaction(), crew)) {
            SiegeRamDiagnostics.serverOnce(
                    worldObj.provider.dimensionId,
                    "spawn-type-reject:" + getUniqueID() + ":"
                            + ramGeneration + ":" + slot,
                    "SPAWN_TYPE_REJECT",
                    "ram=" + getUniqueID() + " gen=" + ramGeneration
                            + " slot=" + slot + " actualClass="
                            + crew.getClass().getName() + " expectedClass="
                            + crewClass.getName() + " actualFaction="
                            + crew.getFaction() + " expectedFaction="
                            + getRamFaction() + " mounted="
                            + (crew.ridingEntity != null)
            );
            SiegeRamCrewOwnershipData.get(worldObj, false).quarantineCarrier(
                    worldObj, getUniqueID(), ramGeneration, slot,
                    "SPAWNED_CARRIER_TYPE_INVALID"
            );
            crew.setDead();
            return;
        }
        SiegeRamCrewOwnershipData data = SiegeRamCrewOwnershipData.get(
                worldObj, false
        );
        if (data == null || !data.prepareSpawn(
                worldObj, getUniqueID(), ramGeneration, slot,
                crew.getUniqueID(), ((int)Math.floor(crew.posX)) >> 4,
                ((int)Math.floor(crew.posZ)) >> 4
        )) {
            crew.setDead();
            return;
        }
        if (!worldObj.spawnEntityInWorld(crew)) {
            SiegeRamDiagnostics.server(
                    "SPAWN_WORLD_RESULT",
                    "ram=" + getUniqueID() + " gen=" + ramGeneration
                            + " slot=" + slot + " crew=" + crew.getUniqueID()
                            + " result=false"
            );
            crew.setDead();
            data.markSpawnFailed(
                    worldObj, getUniqueID(), ramGeneration, slot,
                    crew.getUniqueID()
            );
            return;
        }
        SiegeRamDiagnostics.server(
                "SPAWN_WORLD_RESULT",
                "ram=" + getUniqueID() + " gen=" + ramGeneration
                        + " slot=" + slot + " crew=" + crew.getUniqueID()
                        + " result=true"
        );
        data.markSpawned(
                worldObj, getUniqueID(), ramGeneration, slot,
                crew.getUniqueID()
        );
        crewReferences[slot] = crew;
        crewEntityUuids[slot] = crew.getUniqueID();
        crewSlotAlive[slot] = true;
        crewRespawnAt[slot] = 0L;
        SiegeNetwork.syncRamCrewAttachment(this, crew, slot, true);
    }

    /** Called only from the ownership handler's bounded deferred queue. */
    public void reconcilePreparedSpawnSlot(int slot) {
        if (!hasDurableOwnership() || worldObj == null || worldObj.isRemote
                || slot < 0 || slot >= CREW_SLOT_COUNT) {
            return;
        }
        SiegeRamCrewOwnershipData data = SiegeRamCrewOwnershipData.get(
                worldObj, false
        );
        SiegeRamCrewOwnershipData.SlotSnapshot value = data == null ? null
                : data.getSlot(getUniqueID(), ramGeneration, slot);
        if (value == null || value.state
                != SiegeRamCrewOwnershipData.SlotState.SPAWN_PREPARED
                || !data.canRetryPrepared(
                worldObj, getUniqueID(), ramGeneration, slot,
                value.expectedCrewUuid)) {
            return;
        }
        AxisAlignedBB bounds = AxisAlignedBB.getBoundingBox(
                value.preparedChunkX * 16, 0.0D, value.preparedChunkZ * 16,
                value.preparedChunkX * 16 + 16, 256.0D,
                value.preparedChunkZ * 16 + 16
        );
        List candidates = worldObj.getEntitiesWithinAABB(
                LOTREntityNPC.class, bounds
        );
        for (Object object : candidates) {
            LOTREntityNPC candidate = (LOTREntityNPC)object;
            if (value.expectedCrewUuid.equals(candidate.getUniqueID())) {
                data.markSpawned(
                        worldObj, getUniqueID(), ramGeneration, slot,
                        candidate.getUniqueID()
                );
                attachLoadedCrewIfValid(candidate);
                return;
            }
        }
        /* The exact chunk in which the candidate was prepared is loaded and
         * contains no candidate. Retrying after the bounded failure delay is
         * safe and does not infer anything about an older carrier. */
        data.markSpawnFailed(
                worldObj, getUniqueID(), ramGeneration, slot,
                value.expectedCrewUuid
        );
    }

    private void prepareAttachedCrew(LOTREntityNPC crew) {
        sanitizeAttachedCrewMount(crew);
        captureAttachedCrewCollisionState(crew);
        suppressAttachedCrewAutonomy(crew);
        crew.func_110163_bv();
        crew.noClip = true;
        crew.fallDistance = 0.0F;
    }

    /**
     * Dismounts every validated ram carrier, but destroys a mount only when
     * LOTR marks the carrier's current LOTRNPCMount as NPC-owned and the
     * bidirectional rider link still points at this carrier. This migrates old
     * ram saves without touching unrelated nearby or player-owned mounts.
     */
    private static void sanitizeAttachedCrewMount(LOTREntityNPC crew) {
        if (crew == null || crew.worldObj == null || crew.worldObj.isRemote) {
            return;
        }
        crew.spawnRidingHorse = false;
        Entity mount = crew.ridingEntity;
        if (mount == null) {
            return;
        }
        boolean currentNpcMount = mount instanceof LOTRNPCMount
                && mount.worldObj == crew.worldObj
                && mount.riddenByEntity == crew;
        boolean generatedNpcMount = currentNpcMount
                && ((LOTRNPCMount)mount).getBelongsToNPC();
        crew.mountEntity((Entity)null);
        if (currentNpcMount) {
            crew.setRidingHorse(false);
        }
        if (generatedNpcMount) {
            if (!mount.isDead) {
                mount.setDead();
            }
        }
    }

    private static void removeAllTasks(EntityAITasks tasks) {
        List taskSnapshot = new ArrayList(tasks.taskEntries);
        for (Object object : taskSnapshot) {
            EntityAITasks.EntityAITaskEntry entry =
                    (EntityAITasks.EntityAITaskEntry)object;
            tasks.removeTask(entry.action);
        }
    }

    public static void suppressAttachedCrewAutonomy(LOTREntityNPC crew) {
        if (crew == null || crew.isDead) {
            return;
        }
        suppressAttachedCrewQuesting(crew);
        /* Capturing can invoke the NPC's own attack-mode hook once. Remove
         * any tasks that hook adds immediately afterward in this same pass. */
        suppressAttachedCrewWeapon(crew);
        suppressAttachedCrewAttackModeRefresh(crew);
        removeAllTasks(crew.tasks);
        removeAllTasks(crew.targetTasks);
        crew.setAttackTarget((EntityLivingBase)null);
        crew.setRevengeTarget((EntityLivingBase)null);
        crew.getNavigator().clearPathEntity();
        crew.getMoveHelper().setMoveTo(
                crew.posX,
                crew.posY,
                crew.posZ,
                0.0D
        );
        crew.moveForward = 0.0F;
        crew.moveStrafing = 0.0F;
        crew.setAIMoveSpeed(0.0F);
        crew.setJumping(false);
        crew.motionX = 0.0D;
        crew.motionY = 0.0D;
        crew.motionZ = 0.0D;
        crew.entityCollisionReduction = 1.0F;
        crew.noClip = true;
        crew.fallDistance = 0.0F;
    }

    private static void suppressAttachedCrewQuesting(
            LOTREntityNPC crew
    ) {
        if (crew == null || crew.questInfo == null) {
            return;
        }
        /*
         * Ram carriers are formation components, not independent quest NPCs.
         * Clear any generic offer inherited from LOTR initialization and make
         * future random offer generation effectively impossible. Server-side
         * interaction is also canceled by RamControlEventHandler.
         */
        crew.questInfo.clearMiniQuestOffer();
        crew.questInfo.setOfferChance(Integer.MAX_VALUE);
    }

    private static void captureAttachedCrewCollisionState(
            LOTREntityNPC crew
    ) {
        NBTTagCompound entityData = crew.getEntityData();
        if (!entityData.getBoolean(CREW_COLLISION_REDUCTION_CAPTURED)) {
            entityData.setBoolean(
                    CREW_COLLISION_REDUCTION_CAPTURED,
                    true
            );
            entityData.setFloat(
                    CREW_COLLISION_REDUCTION,
                    crew.entityCollisionReduction
            );
        }
        crew.entityCollisionReduction = 1.0F;
    }

    private void positionCrew(LOTREntityNPC crew, int slot) {
        applyCrewFormation(this, crew, slot, false);
        suppressAttachedCrewWeapon(crew);
    }

    /**
     * Applies the single shared ten-slot transform on either logical side.
     * The client path derives its previous pose from the ram too, preventing
     * the carrier from interpolating independently between server packets.
     */
    public static void applyCrewFormation(
            EntityBattleRam ram,
            LOTREntityNPC crew,
            int slot,
            boolean clientPresentation
    ) {
        if (ram == null
                || crew == null
                || slot < 0
                || slot >= CREW_SLOT_COUNT) {
            return;
        }
        double localX = CREW_OFFSETS[slot][0];
        double localZ = CREW_OFFSETS[slot][1];
        double angle = Math.toRadians(ram.rotationYaw);
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        double offsetX = localX * cos - localZ * sin;
        double offsetZ = localX * sin + localZ * cos;
        double targetX = ram.posX + offsetX;
        double targetZ = ram.posZ + offsetZ;
        double targetY = ram.posY;

        if (ram.getRamState() == BattleRamState.ATTACK_GATE
                && ram.worldObj != null) {
            double groundedY = findAttackCrewGroundY(
                    ram.worldObj,
                    crew,
                    targetX,
                    ram.posY,
                    targetZ
            );
            if (!Double.isNaN(groundedY)) {
                double deltaY = groundedY - ram.posY;
                if (deltaY <= ATTACK_CREW_MAX_GROUND_RISE
                        && deltaY >= -ATTACK_CREW_MAX_GROUND_DROP) {
                    targetY = groundedY;
                }
            }
        }

        crew.setPosition(
                targetX,
                targetY,
                targetZ
        );
        if (clientPresentation) {
            double previousAngle = Math.toRadians(ram.prevRotationYaw);
            double previousCos = Math.cos(previousAngle);
            double previousSin = Math.sin(previousAngle);
            double previousOffsetX = localX * previousCos
                    - localZ * previousSin;
            double previousOffsetZ = localX * previousSin
                    + localZ * previousCos;
            double previousX = ram.lastTickPosX + previousOffsetX;
            double previousY = ram.lastTickPosY;
            double previousZ = ram.lastTickPosZ + previousOffsetZ;

            if (ram.getRamState() == BattleRamState.ATTACK_GATE
                    && ram.worldObj != null) {
                double previousGroundedY = findAttackCrewGroundY(
                        ram.worldObj,
                        crew,
                        previousX,
                        ram.lastTickPosY,
                        previousZ
                );
                if (!Double.isNaN(previousGroundedY)) {
                    double previousDeltaY =
                            previousGroundedY - ram.lastTickPosY;
                    if (previousDeltaY <= ATTACK_CREW_MAX_GROUND_RISE
                            && previousDeltaY >= -ATTACK_CREW_MAX_GROUND_DROP) {
                        previousY = previousGroundedY;
                    }
                }
            }

            crew.prevPosX = previousX;
            crew.prevPosY = previousY;
            crew.prevPosZ = previousZ;
            crew.lastTickPosX = previousX;
            crew.lastTickPosY = previousY;
            crew.lastTickPosZ = previousZ;
        } else {
            crew.prevPosX = crew.posX;
            crew.prevPosY = crew.posY;
            crew.prevPosZ = crew.posZ;
        }
        float formationYaw = ram.rotationYaw;
        float previousFormationYaw = ram.prevRotationYaw;
        crew.rotationYaw = formationYaw;
        crew.prevRotationYaw = previousFormationYaw;
        crew.rotationPitch = 0.0F;
        crew.prevRotationPitch = 0.0F;
        crew.rotationYawHead = formationYaw;
        crew.prevRotationYawHead = previousFormationYaw;
        crew.renderYawOffset = formationYaw;
        crew.prevRenderYawOffset = previousFormationYaw;
        crew.motionX = 0.0D;
        crew.motionY = 0.0D;
        crew.motionZ = 0.0D;
        crew.onGround = true;
        crew.fallDistance = 0.0F;
        crew.moveForward = 0.0F;
        crew.moveStrafing = 0.0F;
        crew.setAIMoveSpeed(0.0F);
        crew.setJumping(false);
        crew.noClip = true;
        if (!clientPresentation) {
            crew.entityCollisionReduction = 1.0F;
        }
    }

    private static double findAttackCrewGroundY(
            World world,
            LOTREntityNPC crew,
            double x,
            double expectedY,
            double z
    ) {
        if (world == null || crew == null) {
            return Double.NaN;
        }

        AxisAlignedBB sample =
                AxisAlignedBB.getBoundingBox(
                        x - ATTACK_CREW_GROUND_SAMPLE_HALF_WIDTH,
                        expectedY - ATTACK_CREW_GROUND_SEARCH_DOWN,
                        z - ATTACK_CREW_GROUND_SAMPLE_HALF_WIDTH,
                        x + ATTACK_CREW_GROUND_SAMPLE_HALF_WIDTH,
                        expectedY + ATTACK_CREW_GROUND_SEARCH_UP,
                        z + ATTACK_CREW_GROUND_SAMPLE_HALF_WIDTH
                );

        List collisions =
                world.getCollidingBoundingBoxes(
                        crew,
                        sample
                );

        double highestSupport = Double.NaN;

        for (Object value : collisions) {
            if (!(value instanceof AxisAlignedBB)) {
                continue;
            }

            AxisAlignedBB box = (AxisAlignedBB)value;

            if (box.maxY > expectedY + ATTACK_CREW_GROUND_SEARCH_UP
                    || box.maxY < expectedY - ATTACK_CREW_GROUND_SEARCH_DOWN) {
                continue;
            }

            if (Double.isNaN(highestSupport)
                    || box.maxY > highestSupport) {
                highestSupport = box.maxY;
            }
        }

        return highestSupport;
    }

    public int getValidAttachedCrewSlot(LOTREntityNPC crew) {
        if (crew == null
                || crew.worldObj == null
                || crew.worldObj != worldObj
                || crew.isDead
                || crew.getHealth() <= 0.0F
                || worldObj == null
                || isDead
                || deliberateDisband
                || !addedToChunk
                || !crew.addedToChunk
                || !crewConfigured
                || !hasDurableOwnership()
                || !BattleRamCrewTypes.isApprovedGroundCrew(
                getRamFaction(), crew)) {
            return -1;
        }
        NBTTagCompound entityData = crew.getEntityData();
        if (!getUniqueID().toString().equals(
                entityData.getString(CREW_RAM_UUID))
                || !entityData.getBoolean(CREW_MARKER)
                || getTaggedRamGeneration(crew) != ramGeneration) {
            return -1;
        }
        int slot = entityData.getInteger(CREW_SLOT);
        if (slot < 0
                || slot >= CREW_SLOT_COUNT
                || !crewSlotAlive[slot]) {
            return -1;
        }
        UUID expectedUuid = crewEntityUuids[slot];
        if (expectedUuid == null
                || !expectedUuid.equals(crew.getUniqueID())
                || !SiegeRamCrewOwnershipData.get(worldObj, false)
                .isCurrentExpected(
                        worldObj, getUniqueID(), ramGeneration, slot,
                        crew.getUniqueID())) {
            return -1;
        }
        LOTREntityNPC referencedCrew = crewReferences[slot];
        return referencedCrew == null || referencedCrew == crew
                ? slot
                : -1;
    }

    public boolean attachLoadedCrewIfValid(LOTREntityNPC crew) {
        int slot = getValidAttachedCrewSlot(crew);
        if (slot < 0) {
            return false;
        }
        boolean associationChanged = crewReferences[slot] != crew;
        crewReferences[slot] = crew;
        ItemStack heldBefore = crew.getHeldItem();
        prepareAttachedCrew(crew);
        NBTTagCompound attachmentData = crew.getEntityData();
        SiegeRamDiagnostics.serverOnce(
                worldObj.provider.dimensionId,
                "server-attach-weapon:" + getUniqueID() + ":" + ramGeneration
                        + ":" + slot + ":" + crew.getUniqueID(),
                "SERVER_ATTACH_WEAPON",
                "ram=" + getUniqueID() + " gen=" + ramGeneration
                        + " slot=" + slot + " crew=" + crew.getUniqueID()
                        + " before=" + describeItem(heldBefore)
                        + " after=" + describeItem(crew.getHeldItem())
                        + " captured="
                        + attachmentData.getBoolean(CREW_HELD_ITEM_CAPTURED)
                        + " captureVersion="
                        + attachmentData.getInteger(
                        CREW_HELD_ITEM_CAPTURE_VERSION
                ) + " savedCapturedItem="
                        + attachmentData.hasKey(CREW_HELD_ITEM)
        );
        SiegeRamCrewOwnershipData.get(worldObj, false).noteCarrierChunk(
                worldObj, getUniqueID(), ramGeneration, slot,
                crew.getUniqueID(), ((int)Math.floor(crew.posX)) >> 4,
                ((int)Math.floor(crew.posZ)) >> 4
        );
        if (associationChanged) {
            SiegeNetwork.syncRamCrewAttachment(
                    this,
                    crew,
                    slot,
                    true
            );
        }
        return true;
    }

    public void reconcileAttachedCrewFormation() {
        if (worldObj == null
                || worldObj.isRemote
                || isDead
                || deliberateDisband
                || !crewConfigured) {
            return;
        }
        SiegeRamCrewOwnershipData data = hasDurableOwnership()
                ? SiegeRamCrewOwnershipData.get(worldObj, false)
                : null;
        for (int slot = 0; slot < CREW_SLOT_COUNT; ++slot) {
            LOTREntityNPC crew = crewReferences[slot];
            if (crew == null || getValidAttachedCrewSlot(crew) != slot) {
                continue;
            }
            suppressAttachedCrewAutonomy(crew);
            applyCrewFormation(this, crew, slot, false);
            /*
             * Keep the durable recovery coordinate current as the formation
             * crosses chunk borders. noteCarrierChunk is a no-op while the
             * carrier remains in the same chunk, so this does not dirty world
             * data every tick.
             */
            noteCurrentCarrierChunk(data, slot, crew);
        }
    }

    public void syncAttachedCrewTo(EntityPlayerMP player) {
        if (player == null || player.worldObj != worldObj) {
            return;
        }
        for (int slot = 0; slot < CREW_SLOT_COUNT; ++slot) {
            LOTREntityNPC crew = crewReferences[slot];
            if (crew != null && getValidAttachedCrewSlot(crew) == slot) {
                SiegeNetwork.syncRamCrewAttachmentTo(
                        player,
                        this,
                        crew,
                        slot,
                        true
                );
            }
        }
    }

    public static UUID getTaggedRamUuid(LOTREntityNPC crew) {
        if (crew == null) {
            return null;
        }
        return readUuid(crew.getEntityData().getString(CREW_RAM_UUID));
    }

    public static boolean hasRamCrewTag(LOTREntityNPC crew) {
        return getTaggedRamUuid(crew) != null;
    }

    public static boolean hasDurableRamCrewTag(LOTREntityNPC crew) {
        return hasRamCrewTag(crew)
                && crew.getEntityData().getBoolean(CREW_MARKER)
                && getTaggedRamGeneration(crew) > 0L;
    }

    public static long getTaggedRamGeneration(LOTREntityNPC crew) {
        return crew == null ? 0L : crew.getEntityData().getLong(CREW_GENERATION);
    }

    public static void tagCrewForRam(
            LOTREntityNPC crew,
            UUID ramUuid,
            long generation,
            int slot
    ) {
        if (crew == null || ramUuid == null || generation <= 0L
                || slot < 0 || slot >= CREW_SLOT_COUNT) {
            return;
        }
        NBTTagCompound data = crew.getEntityData();
        data.setString(CREW_RAM_UUID, ramUuid.toString());
        data.setLong(CREW_GENERATION, generation);
        data.setInteger(CREW_SLOT, slot);
        data.setBoolean(CREW_MARKER, true);
    }

    public boolean migrateLegacyCarrierIfValid(LOTREntityNPC crew) {
        if (crew == null || !hasRamCrewTag(crew) || hasDurableRamCrewTag(crew)
                || !hasDurableOwnership()
                || !getUniqueID().equals(getTaggedRamUuid(crew))) {
            return false;
        }
        int slot = getTaggedCrewSlot(crew);
        SiegeRamCrewOwnershipData data = SiegeRamCrewOwnershipData.get(
                worldObj, false
        );
        if (slot < 0 || data == null
                || !BattleRamCrewTypes.isApprovedGroundCrew(getRamFaction(), crew)
                || !data.isCurrentExpected(
                worldObj, getUniqueID(), ramGeneration, slot,
                crew.getUniqueID())) {
            return false;
        }
        tagCrewForRam(crew, getUniqueID(), ramGeneration, slot);
        return attachLoadedCrewIfValid(crew);
    }

    public static int getTaggedCrewSlot(LOTREntityNPC crew) {
        if (crew == null) {
            return -1;
        }
        int slot = crew.getEntityData().getInteger(CREW_SLOT);
        return slot >= 0 && slot < CREW_SLOT_COUNT ? slot : -1;
    }

    private static void suppressAttachedCrewWeapon(LOTREntityNPC crew) {
        if (crew == null || crew.worldObj == null || crew.worldObj.isRemote) {
            return;
        }
        NBTTagCompound entityData = crew.getEntityData();
        /*
         * New LOTR NPCs are positioned once before onSpawnWithEgg assigns
         * their faction equipment. Wait until the ram association tag exists
         * so the authored weapon is captured after that initialization.
         */
        if (!entityData.hasKey(CREW_RAM_UUID)) {
            return;
        }
        boolean capturedBeforeSuppression = entityData.getInteger(
                CREW_HELD_ITEM_CAPTURE_VERSION
        ) >= CURRENT_HELD_ITEM_CAPTURE_VERSION;
        if (entityData.getInteger(CREW_HELD_ITEM_CAPTURE_VERSION)
                < CURRENT_HELD_ITEM_CAPTURE_VERSION) {
            /*
             * onSpawnWithEgg fills LOTR's NPC inventory, but the first normal
             * combat-mode update may not yet have copied its intended item to
             * equipment slot 0. Materialize that authoritative mode now, once,
             * before taking the persistent snapshot. Existing non-null saved
             * snapshots are never overwritten during migration.
             */
            if (!entityData.hasKey(CREW_HELD_ITEM)) {
                crew.refreshCurrentAttackMode();
                ItemStack heldItem = crew.getHeldItem();
                if (heldItem != null) {
                    NBTTagCompound heldItemNbt = new NBTTagCompound();
                    heldItem.writeToNBT(heldItemNbt);
                    entityData.setTag(CREW_HELD_ITEM, heldItemNbt);
                }
            }
            entityData.setBoolean(CREW_HELD_ITEM_CAPTURED, true);
            entityData.setInteger(
                    CREW_HELD_ITEM_CAPTURE_VERSION,
                    CURRENT_HELD_ITEM_CAPTURE_VERSION
            );
        }
        ItemStack heldItem = crew.getHeldItem();
        if (capturedBeforeSuppression && heldItem != null) {
            SiegeRamDiagnostics.serverOnce(
                    crew.worldObj.provider.dimensionId,
                    "server-weapon-reappeared:"
                            + entityData.getString(CREW_RAM_UUID) + ":"
                            + entityData.getLong(CREW_GENERATION) + ":"
                            + entityData.getInteger(CREW_SLOT) + ":"
                            + crew.getUniqueID(),
                    "SERVER_WEAPON_REAPPEARED",
                    "ram=" + entityData.getString(CREW_RAM_UUID)
                            + " gen=" + entityData.getLong(CREW_GENERATION)
                            + " slot=" + entityData.getInteger(CREW_SLOT)
                            + " crew=" + crew.getUniqueID() + " item="
                            + describeItem(heldItem)
            );
        }
        if (heldItem != null) {
            if (capturedBeforeSuppression) {
                SiegeRamDiagnostics.serverOnce(
                        crew.worldObj.provider.dimensionId,
                        "server-weapon-post-refresh-cleared:"
                                + entityData.getString(CREW_RAM_UUID) + ":"
                                + entityData.getLong(CREW_GENERATION) + ":"
                                + entityData.getInteger(CREW_SLOT) + ":"
                                + crew.getUniqueID(),
                        "SERVER_WEAPON_POST_REFRESH_CLEARED",
                        "ram=" + entityData.getString(CREW_RAM_UUID)
                                + " gen=" + entityData.getLong(
                                CREW_GENERATION
                        ) + " slot=" + entityData.getInteger(
                                CREW_SLOT
                        ) + " crew=" + crew.getUniqueID()
                                + " item=" + describeItem(heldItem)
                );
            }
            crew.setCurrentItemOrArmor(0, (ItemStack)null);
        }
    }

    /**
     * LOTR 36.15 replays an NPC's attack-mode callback on the first update
     * after NBT load. Several mapped troop base classes implement that
     * callback by assigning slot 0 from npcItemsInv, which happens after the
     * ordinary pre-update Forge event. A ram carrier has no combat autonomy,
     * so freeze only this private LOTR runtime mode at IDLE after our one-time
     * captured-weapon snapshot has been made. The inventory and captured item
     * remain untouched for genuine-death restoration.
     */
    private static void suppressAttachedCrewAttackModeRefresh(
            LOTREntityNPC crew
    ) {
        if (crew == null
                || NPC_CURRENT_ATTACK_MODE_FIELD == null
                || NPC_FIRST_UPDATED_ATTACK_MODE_FIELD == null
                || NPC_IDLE_ATTACK_MODE == null) {
            if (crew != null && crew.worldObj != null) {
                SiegeRamDiagnostics.serverOnce(
                        crew.worldObj.provider.dimensionId,
                        "server-attack-mode-freeze-unavailable:"
                                + crew.getUniqueID(),
                        "SERVER_WEAPON_FREEZE_UNAVAILABLE",
                        "crew=" + crew.getUniqueID()
                );
            }
            return;
        }
        try {
            NPC_CURRENT_ATTACK_MODE_FIELD.set(crew, NPC_IDLE_ATTACK_MODE);
            NPC_FIRST_UPDATED_ATTACK_MODE_FIELD.setBoolean(crew, true);
        } catch (IllegalAccessException exception) {
            SiegeRamDiagnostics.serverOnce(
                    crew.worldObj.provider.dimensionId,
                    "server-attack-mode-freeze-failed:" + crew.getUniqueID(),
                    "SERVER_WEAPON_FREEZE_FAILED",
                    "crew=" + crew.getUniqueID() + " error="
                            + exception.getClass().getSimpleName()
            );
        } catch (IllegalArgumentException exception) {
            SiegeRamDiagnostics.serverOnce(
                    crew.worldObj.provider.dimensionId,
                    "server-attack-mode-freeze-failed:" + crew.getUniqueID(),
                    "SERVER_WEAPON_FREEZE_FAILED",
                    "crew=" + crew.getUniqueID() + " error="
                            + exception.getClass().getSimpleName()
            );
        }
    }

    private static Field getNpcAttackModeField(String name) {
        try {
            Field field = LOTREntityNPC.class.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException exception) {
            return null;
        } catch (SecurityException exception) {
            return null;
        }
    }

    private static Object getNpcIdleAttackMode() {
        try {
            Class<?> attackMode = Class.forName(
                    "lotr.common.entity.npc.LOTREntityNPC$AttackMode"
            );
            Field idle = attackMode.getDeclaredField("IDLE");
            idle.setAccessible(true);
            return idle.get(null);
        } catch (ClassNotFoundException exception) {
            return null;
        } catch (NoSuchFieldException exception) {
            return null;
        } catch (IllegalAccessException exception) {
            return null;
        } catch (SecurityException exception) {
            return null;
        }
    }

    private static String describeItem(ItemStack item) {
        if (item == null || item.getItem() == null) {
            return "null";
        }
        return item.getItem().getUnlocalizedName() + ":" + item.getItemDamage();
    }

    private static String conciseMessage(String message) {
        if (message == null || message.isEmpty()) {
            return "none";
        }
        String result = message.replace('\n', ' ').replace('\r', ' ');
        return result.length() <= 160 ? result : result.substring(0, 160);
    }

    /**
     * LivingDeathEvent fires before vanilla/LOTR drop processing. Restoring
     * here keeps a carrier's original equipment semantics on a real death
     * while attached rendering remains weaponless during life. Disband uses
     * setDead(), so it intentionally produces neither restoration nor drops.
     */
    public static void restoreAttachedCrewWeaponForDeath(
            LOTREntityNPC crew
    ) {
        if (crew == null || crew.worldObj == null || crew.worldObj.isRemote) {
            return;
        }
        NBTTagCompound entityData = crew.getEntityData();
        if (!entityData.getBoolean(CREW_HELD_ITEM_CAPTURED)) {
            return;
        }
        ItemStack heldItem = null;
        if (entityData.hasKey(CREW_HELD_ITEM)) {
            heldItem = ItemStack.loadItemStackFromNBT(
                    entityData.getCompoundTag(CREW_HELD_ITEM)
            );
        }
        crew.setCurrentItemOrArmor(0, heldItem);
        entityData.removeTag(CREW_HELD_ITEM);
        entityData.removeTag(CREW_HELD_ITEM_CAPTURED);
        entityData.removeTag(CREW_HELD_ITEM_CAPTURE_VERSION);
    }

    /** Explicit no-drop cleanup for stale or tombstoned carriers only. */
    public static void retireCrewWithoutDeath(LOTREntityNPC crew) {
        if (crew == null || crew.worldObj == null || crew.worldObj.isRemote) {
            return;
        }
        SiegeNetwork.syncRamCrewDetachment(crew);
        crew.setDead();
    }

    private static UUID readUuid(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private enum PhysicalAttackPhase {
        NONE,
        READYING,
        BACKING_UP,
        BRACING,
        CHARGING,
        RECOVERING
    }

    private static final class QueuedGateTarget {
        private final int dimensionId;
        private final int controllerX;
        private final int controllerY;
        private final int controllerZ;
        private final UUID gateUuid;

        private QueuedGateTarget(
                int dimensionId,
                int controllerX,
                int controllerY,
                int controllerZ,
                UUID gateUuid
        ) {
            this.dimensionId = dimensionId;
            this.controllerX = controllerX;
            this.controllerY = controllerY;
            this.controllerZ = controllerZ;
            this.gateUuid = gateUuid;
        }

        private static QueuedGateTarget fromGate(TileEntitySiegeGate gate) {
            return new QueuedGateTarget(
                    gate.getWorldObj().provider.dimensionId,
                    gate.xCoord,
                    gate.yCoord,
                    gate.zCoord,
                    gate.getGateUuid()
            );
        }

        private boolean matches(TileEntitySiegeGate gate) {
            if (gate == null
                    || gate.getWorldObj() == null
                    || gate.getWorldObj().provider.dimensionId != dimensionId
                    || gate.xCoord != controllerX
                    || gate.yCoord != controllerY
                    || gate.zCoord != controllerZ) {
                return false;
            }
            return gateUuid == null
                    || gate.getGateUuid() == null
                    || gateUuid.equals(gate.getGateUuid());
        }
    }

    public static final class TargetQueueSnapshot {
        private final int dimensionId;
        private final int controllerX;
        private final int controllerY;
        private final int controllerZ;

        private TargetQueueSnapshot(
                int dimensionId,
                int controllerX,
                int controllerY,
                int controllerZ
        ) {
            this.dimensionId = dimensionId;
            this.controllerX = controllerX;
            this.controllerY = controllerY;
            this.controllerZ = controllerZ;
        }

        public int getDimensionId() {
            return dimensionId;
        }

        public int getControllerX() {
            return controllerX;
        }

        public int getControllerY() {
            return controllerY;
        }

        public int getControllerZ() {
            return controllerZ;
        }
    }

    private static final class AttackPoint {
        private final double approachX;
        private final double approachY;
        private final double approachZ;
        private final double impactX;
        private final double impactY;
        private final double impactZ;
        private final GatePartData impactPart;

        private AttackPoint(
                double approachX,
                double approachY,
                double approachZ,
                double impactX,
                double impactY,
                double impactZ,
                GatePartData impactPart
        ) {
            this.approachX = approachX;
            this.approachY = approachY;
            this.approachZ = approachZ;

            this.impactX = impactX;
            this.impactY = impactY;
            this.impactZ = impactZ;

            this.impactPart =
                    impactPart;
        }
    }
}
