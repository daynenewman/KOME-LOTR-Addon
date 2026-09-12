package com.enovak.lotrmoremobs.entity.animal;

import com.enovak.lotrmoremobs.Main;
import com.enovak.lotrmoremobs.config.MumakilConfig;
import com.enovak.lotrmoremobs.achievement.MumakilAchievements;
import com.enovak.lotrmoremobs.entity.npc.LOTREntityMumakilHowdahArcher;
import com.enovak.lotrmoremobs.handler.MumakilDriverControlEventHandler;
import com.enovak.lotrmoremobs.handler.MumakilHowdahArcherEventHandler;
import com.enovak.lotrmoremobs.inventory.ContainerMumakilInventory;
import com.enovak.lotrmoremobs.spawning.MumakilWarFormationFactory;
import com.enovak.lotrmoremobs.util.MumakilPerformanceTracker;
import com.enovak.lotrmoremobs.util.MumakilServerPerformanceDiagnostics;
import cpw.mods.fml.common.registry.IThrowableEntity;
import lotr.common.LOTRCommonProxy;
import lotr.common.LOTRMod;
import lotr.common.LOTRReflection;
import lotr.common.entity.LOTREntityUtils;
import lotr.common.entity.LOTREntityInvasionSpawner;
import lotr.common.entity.ai.LOTREntityAIAttackOnCollide;
import lotr.common.entity.ai.LOTREntityAIHorseFollowHiringPlayer;
import lotr.common.entity.ai.LOTREntityAIHorseMoveToRiderTarget;
import lotr.common.entity.animal.LOTREntityHorse;
import lotr.common.entity.npc.LOTREntityNPC;
import lotr.common.fac.LOTRFaction;
import net.minecraft.block.Block;
import net.minecraft.command.IEntitySelector;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityAgeable;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.entity.IEntityLivingData;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.ai.EntityAIHurtByTarget;
import net.minecraft.entity.ai.EntityAIMate;
import net.minecraft.entity.ai.EntityAINearestAttackableTarget;
import net.minecraft.entity.ai.EntityAIPanic;
import net.minecraft.entity.ai.EntityAITasks;
import net.minecraft.entity.ai.RandomPositionGenerator;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.passive.EntityHorse;
import net.minecraft.entity.passive.EntityTameable;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.entity.projectile.EntityFireball;
import net.minecraft.entity.projectile.EntityThrowable;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.pathfinding.PathEntity;
import net.minecraft.pathfinding.PathPoint;
import net.minecraft.stats.StatList;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraft.world.SpawnerAnimals;
import software.bernie.geckolib3.core.IAnimatable;
import software.bernie.geckolib3.core.manager.AnimationData;
import software.bernie.geckolib3.core.manager.AnimationFactory;
import net.minecraft.entity.ai.EntityAIFollowParent;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class LOTREntityMumakil extends LOTREntityHorse implements IAnimatable {

    // ---------------------------------------------------------------------
    // Build / debug flags
    // ---------------------------------------------------------------------

    private static final boolean DEBUG_COMBAT_LOGS = false;
    private static final boolean DEBUG_AUTONOMOUS_COMBAT_AI = false;
    private static final boolean DEBUG_MUMAKIL_MODE = false;
    private static final boolean DEBUG_BABY_FAMILY_AI = false; // BABY_FAMILY_AI_DIAGNOSTICS_V1
    private static final String MIDDLE_EARTH_TWEAKS_RIDER_TARGET_AI_CLASS =
            "metweaks.guards.customranged.HorseMoveToRiderTargetAiFix";

    // LOTRMoreMobs Mumakil entity patch: STRIKE_TIMER_SOUND_MAPPING_V12_4_NORMAL_HIT_SOUND_2026_06_28

    // ---------------------------------------------------------------------
    // Base stats
    // ---------------------------------------------------------------------

    private static final double MAX_HEALTH = 3000.0D;
    private static final double MOVEMENT_SPEED = 0.30D;
    private static final double BABY_MAX_HEALTH = 300.0D;
    private static final double BABY_MOVEMENT_SPEED = 0.4D;
    public static final float MIN_SPEED_TRAIT = 0.85F;
    public static final float NORMAL_SPEED_TRAIT = 1.00F;
    public static final float MAX_SPEED_TRAIT = 1.15F;
    private static final double SPEED_TRAIT_MUTATION_STANDARD_DEVIATION =
            0.015D;
    private static final double PLAYER_RIDDEN_SPEED_MULTIPLIER = 0.5D; // BABY_INVENTORY_AND_PLAYER_SPEED_FIX_V1
    private static final UUID PLAYER_RIDDEN_SPEED_MODIFIER_UUID = UUID.fromString("2bf8c4fe-3875-4e51-a285-46c1dfa31488");
    // Baby behavior: BABY_FOLLOW_AND_PANIC_V1
    private static final double BABY_PANIC_SPEED = 0.6D;
    private static final double BABY_FOLLOW_SPEED = 0.4D;
    private static final double BABY_FOLLOW_RANGE = 32.0D;
    private static final double BABY_FOLLOW_VERTICAL_RANGE = 8.0D;
    private static final double BABY_FOLLOW_START_DISTANCE_SQ = 144.0D;
    private static final double BABY_FOLLOW_STOP_DISTANCE_SQ = 81.0D;
    private static final double BABY_FOLLOW_MAX_DISTANCE_SQ = 1024.0D;
    private static final double BABY_FOLLOW_REMEMBERED_MAX_DISTANCE_SQ = 4096.0D;
    private static final int BABY_FOLLOW_SEARCH_INTERVAL = 5;
    private static final int BABY_FOLLOW_REPATH_INTERVAL = 10;
    private static final double ADULT_PATH_SEARCH_RANGE = 32.0D;
    private static final double BABY_PATH_SEARCH_RANGE = 48.0D;
    // Baby melon lure: BABY_MELON_LURE_V1 / BABY_MELON_LURE_NEAREST_HOLDER_V2
    private static final double BABY_MELON_FOLLOW_SPEED = 0.5D;
    private static final double BABY_MELON_FOLLOW_RANGE = 16.0D;
    private static final double BABY_MELON_STOP_DISTANCE_SQ = 25.0D;
    private static final double BABY_MELON_MAX_DISTANCE_SQ = 400.0D;
    private static final int BABY_MELON_SEARCH_INTERVAL = 10;
    private static final int BABY_MELON_REPATH_INTERVAL = 10;
    private static final int BABY_MELON_FED_WINDOW_TICKS = 5 * 60 * 20; // BABY_MELON_FED_WINDOW_V1
    private static final float TAMED_BABY_MELON_HEAL_AMOUNT = 20.0F; // TAMED_BABY_MELON_HEALING_V1
    private static final int TAMED_BABY_MELON_HEAL_COOLDOWN_TICKS = 30 * 20;
    private static final int BABY_GROWTH_TICKS = 30 * 20 * 60;
    //30 * 60 * 20
    private static final int NATURAL_HERD_FAMILY_CHANCE = 4; // MUMAKIL_NATURAL_SPAWNING_V1
    private static final int NATURAL_HERD_ADULTS_BEFORE_BABIES = 2;
    private static final int NATURAL_HERD_MAX_BABIES = 2;
    private static final double NATURAL_HERD_SPACING_RADIUS = 128.0D;
    private static final double NATURAL_HERD_MEMBER_MIN_RADIUS = 9.0D;
    private static final double NATURAL_HERD_MEMBER_MAX_RADIUS = 13.0D;
    private static final int NATURAL_HERD_MEMBER_POSITION_ATTEMPTS = 12;
    private static final double BABY_TAMING_ADULT_PROTECTION_RANGE = 24.0D; // BABY_TAMING_ADULT_PROTECTION_V1 / OCCASIONAL_PLAYER_AGGRESSION_V1
    private static final double BABY_TAMING_ADULT_PROTECTION_VERTICAL_RANGE = 16.0D;

    private static final float ADULT_WIDTH = 7.0F;
    private static final float ADULT_HEIGHT = 15.0F;
    private static final float BABY_RENDER_SCALE = 1.0F / 3.0F;
    private static final float BABY_WIDTH = 2.333F;
    private static final float BABY_HEIGHT = 5.0F;
    private static final int AMBIENT_TRUMPET_INTERVAL_TICKS = 360;
    private static final int AMBIENT_TRUMPET_CHANCE_MODULO = 4;
    private static final int MUMAKIL_TRUMPET_ANIMATION_TICKS = 53;
    /*
     * At 0.40 seconds (8 ticks), the base trunk has completed roughly 46%
     * of its 0.875-second raise and every trunk segment has begun moving.
     */
    public static final int MUMAKIL_TRUMPET_SOUND_DELAY_TICKS = 8;
    private static final int AMBIENT_EAR_FLAP_INTERVAL_TICKS = 180;
    private static final int AMBIENT_EAR_FLAP_CHANCE_MODULO = 2;

    private static final double KNOCKBACK_RESISTANCE = 20.0D;
    private static final double ATTACK_DAMAGE = 16.0D;

    // ---------------------------------------------------------------------
    // Rider positioning
    // Forward = positive value moves rider toward Mumakil head.
    // Side = positive value moves rider to Mumakil right side.
    // ---------------------------------------------------------------------

    private static final double RIDER_WILD_FORWARD = 4.0D;
    private static final double RIDER_WILD_SIDE = 0.0D;
    private static final double RIDER_WILD_Y = 16.0D;

    private static final double RIDER_SADDLE_FORWARD = 4.0D;
    private static final double RIDER_SADDLE_SIDE = 0.0D;
    private static final double RIDER_SADDLE_Y = 16.0D;

    private static final double RIDER_HOWDAH_FORWARD = 9.8D;
    private static final double RIDER_HOWDAH_SIDE = 0.0D;
    private static final double RIDER_HOWDAH_Y = 17.0D;

    private static final boolean DEBUG_PLAYER_SEAT_AND_ARROW_ORIGIN = false;
    private static final double DEBUG_PLAYER_SEAT_FIRST_DISTANCE = 10.0D;
    private static final double DEBUG_PLAYER_SEAT_SECOND_DISTANCE = 30.0D;

    // ---------------------------------------------------------------------
    // Wild movement
    // ---------------------------------------------------------------------

    private static final double WILD_WANDER_SPEED = 0.22D;
    private static final double WILD_CHASE_SPEED = 0.40D;
    private static final int WILD_CHASE_REPATH_INTERVAL = 25;
    private static final int WILD_WANDER_MIN_INTERVAL = 80;
    private static final int WILD_WANDER_RANDOM_INTERVAL = 81;
    private static final int WILD_WANDER_RADIUS = 16;
    private static final int WILD_WANDER_VERTICAL_RANGE = 4;
    private static final int WILD_FALLBACK_MOVE_TICKS = 30;
    private static final float WILD_FALLBACK_TURN_STEP = 8.0F;
    private static final double WILD_CHASE_FALLBACK_ACCELERATION = 0.08D;
    private static final double WILD_WANDER_FALLBACK_ACCELERATION = 0.04D;
    private static final double WILD_CHASE_FALLBACK_MAX_SPEED = 0.28D;
    private static final double WILD_WANDER_FALLBACK_MAX_SPEED = 0.14D;
    private static final double WILD_WANDER_FALLBACK_STOP_RANGE = 3.0D;

    private static final int MOB_TARGET_CHECK_INTERVAL = 20;
    private static final double MOB_TARGET_RANGE = 18.0D;
    private static final double MOB_TARGET_VERTICAL_RANGE = 8.0D;
    private static final double WILD_TARGET_RETENTION_RANGE = 32.0D;
    private static final int WILD_TARGET_UNSEEN_MEMORY_TICKS = 60;

    private static final int ANGER_WAVE_MIN_DURATION = 60;
    private static final int ANGER_WAVE_RANDOM_DURATION = 61;
    private static final int ANGER_WAVE_MIN_COOLDOWN = 180;
    private static final int ANGER_WAVE_RANDOM_COOLDOWN = 81;
    public static final double TERRITORIAL_WARNING_RADIUS = 18.0D;
    public static final double TERRITORIAL_IMMEDIATE_AGGRESSION_RADIUS =
            10.0D;
    public static final double TERRITORIAL_WARNING_CANCEL_RADIUS = 24.0D;
    public static final int TERRITORIAL_WARNING_DURATION_TICKS = 60;
    public static final int TERRITORIAL_WARNING_MEMORY_TICKS = 400;
    private static final int TERRITORIAL_WARNING_MEMORY_SLOTS = 4;
    public static final double WILD_HERD_EVENT_RADIUS = 30.0D;
    public static final int WILD_HERD_EVENT_COOLDOWN_TICKS = 200;
    private static final int WILD_HERD_REGROUP_DURATION_TICKS = 160;
    private static final int WILD_HERD_REPATH_COOLDOWN_TICKS = 20;
    private static final double WILD_HERD_ADULT_RING_MIN = 6.0D;
    private static final double WILD_HERD_ADULT_RING_RANGE = 4.0D;
    private static final double WILD_HERD_REGROUP_START_DISTANCE = 10.0D;
    private static final double WILD_HERD_REGROUP_COMPLETE_DISTANCE = 8.0D;
    private static final double WILD_HERD_REGROUP_START_DISTANCE_SQ =
            WILD_HERD_REGROUP_START_DISTANCE
                    * WILD_HERD_REGROUP_START_DISTANCE;
    private static final double WILD_HERD_REGROUP_COMPLETE_DISTANCE_SQ =
            WILD_HERD_REGROUP_COMPLETE_DISTANCE
                    * WILD_HERD_REGROUP_COMPLETE_DISTANCE;

    private static final String NBT_HIRED_WAR_MUMAKIL = "lotrmoremobs_hiredWarMumakil";
    private static final String NBT_MUMAKIL_MODE = "lotrmoremobs_mumakilMode";
    private static final String NBT_FORMATION_ORIGIN = "lotrmoremobs_formationOrigin";
    private static final String NBT_HAS_MUMAKIL_HOWDAH =
            "lotrmoremobs_hasMumakilHowdah";
    public static final String NBT_SPEED_TRAIT = "lotrmoremobs_speedTrait";
    public static final String NBT_HIRED_FORMATION_OWNER =
            "lotrmoremobs_hiredFormationOwner";
    public static final String NBT_MUMAK_INVASION_ID =
            "lotrmoremobs_mumakInvasionId";
    private static final String NBT_BABY_GROWTH_INITIALIZED = "lotrmoremobs_babyGrowthInitialized";
    private static final String NBT_BABY_MELON_FED_UNTIL = "lotrmoremobs_babyMelonFedUntilTick";
    private static final String NBT_TAMED_BABY_MELON_HEAL_UNTIL = "lotrmoremobs_tamedBabyMelonHealUntilTick";
    private static final String NBT_CAPTIVE_BRED = "lotrmoremobs_captiveBred";
    private static final int HOWDAH_ROSTER_LOAD_GRACE_TICKS = 160;

    // ---------------------------------------------------------------------
    // Combat / tusk attack / trample
    // ---------------------------------------------------------------------

    private static final double WILD_ATTACK_SPEED = 1.30D;
    private static final int COMBAT_PATH_REPATH_COOLDOWN = 20;
    private static final int COMBAT_PATH_NO_PATH_RETRY_COOLDOWN = 100;
    private static final int COMBAT_PATH_FAILURE_BACKOFF_MIN = 40;
    private static final int COMBAT_PATH_FAILURE_BACKOFF_MAX = 100;
    private static final int COMBAT_PATH_STAGGER_TICKS = 5;
    private static final int COMBAT_PATH_PROGRESS_CHECK_TICKS = 20;
    private static final int COMBAT_PATH_NO_PROGRESS_TICKS = 60;
    private static final double COMBAT_PATH_TARGET_MOVE_THRESHOLD_SQ = 36.0D;
    private static final double COMBAT_DIRECT_PATH_MAX_RANGE = 16.0D;
    private static final double COMBAT_DIRECT_PATH_MAX_Y_DIFFERENCE = 3.0D;
    private static final double COMBAT_PATH_PROGRESS_THRESHOLD_SQ = 1.0D;

    private static final double TUSK_ATTACK_RANGE = 8.0D; // MUMAKIL_EDGE_MELEE_AND_REACH_V1
    private static final int TUSK_ATTACK_COOLDOWN_TICKS = 160;
    private static final double TUSK_ATTACK_FRONT_CONE_DOT = 0.3D;
    private static final double TUSK_ATTACK_CLOSE_RANGE = 2.5D;
    private static final double AUTONOMOUS_ATTACK_PASS_TRIGGER_DISTANCE =
            18.0D;
    private static final double AUTONOMOUS_PASS_THROUGH_DISTANCE = 11.0D;
    private static final double AUTONOMOUS_PASS_MIN_TARGET_DISTANCE = 8.0D;
    private static final double AUTONOMOUS_PASS_MAX_TARGET_DISTANCE = 14.0D;
    private static final double AUTONOMOUS_TURNAROUND_DISTANCE = 16.0D;
    private static final double AUTONOMOUS_TURNAROUND_MIN_TARGET_DISTANCE =
            8.0D;
    private static final double AUTONOMOUS_TURNAROUND_MAX_TARGET_DISTANCE =
            24.0D;
    private static final double AUTONOMOUS_WAYPOINT_REACHED_DISTANCE = 4.0D;
    private static final double AUTONOMOUS_TARGET_WAYPOINT_REPATH_DISTANCE_SQ =
            36.0D;
    private static final double AUTONOMOUS_CLUSTER_SCAN_RANGE = 20.0D;
    private static final double AUTONOMOUS_CLUSTER_SCAN_VERTICAL_RANGE = 8.0D;
    private static final int AUTONOMOUS_CLUSTER_CANDIDATE_LIMIT = 32;
    private static final double AUTONOMOUS_TRAMPLE_CORRIDOR_RADIUS = 4.5D;
    private static final int AUTONOMOUS_PASS_REPATH_INTERVAL = 15;
    private static final int AUTONOMOUS_TURN_REPATH_INTERVAL = 20;
    private static final int AUTONOMOUS_WAYPOINT_MAX_FAILED_SEARCHES = 3;
    private static final int AUTONOMOUS_WAYPOINT_STAGGER_TICKS = 5;
    private static final int AUTONOMOUS_STRIKE_FACING_LOCK_TICKS = 6;
    private static final double[] AUTONOMOUS_PASS_ANGLES =
            new double[] {0.0D, 20.0D, -20.0D, 40.0D, -40.0D};
    private static final double[] AUTONOMOUS_TURNAROUND_ANGLES =
            new double[] {75.0D, -75.0D, 110.0D, -110.0D, 45.0D, -45.0D};

    private static final float TUSK_AOE_DAMAGE = 16.0F;
    private static final double TUSK_AOE_RADIUS = 4.25D;
    private static final double TUSK_AOE_VERTICAL_RANGE = 2.25D;
    private static final float TUSK_AOE_KNOCKBACK_HORIZONTAL = 5.0F;
    private static final float TUSK_AOE_KNOCKBACK_VERTICAL = 0.75F; // MUMAKIL_EDGE_MELEE_AND_REACH_V1

    private static final int TRAMPLE_SCAN_INTERVAL = 2;
    private static final int TRAMPLE_COOLDOWN_TICKS = 30; // TRAMPLE_GLOBAL_COOLDOWN_NO_EFFECTS_V1
    private static final float TRAMPLE_MIN_SPEED = 0.10F;
    private static final float TRAMPLE_DAMAGE = 8.0F;

    private static final int PROJECTILE_HURT_RESISTANT_TICKS = 6;
    private static final int FORMATION_THREAT_MEMORY_TICKS = 100;

    // ---------------------------------------------------------------------
    // Tree/obstacle clearing
    // ---------------------------------------------------------------------

    private static final int AGGRO_OBSTACLE_CLEAR_INTERVAL = 3;
    private static final int AGGRO_OBSTACLE_SWEEP_RETRY_TICKS = 12;
    private static final int AGGRO_OBSTACLE_BROKEN_RETRY_TICKS = 6;
    private static final int AGGRO_OBSTACLE_PROGRESS_CHECK_TICKS = 20;
    private static final int AGGRO_OBSTACLE_SLICE_COUNT = 13;
    private static final int AGGRO_OBSTACLE_BLOCK_CHECK_BUDGET = 320;
    private static final int MAX_OBSTACLES_PER_PASS = 48;
    private static final double AGGRO_OBSTACLE_PROGRESS_DISTANCE_SQ = 1.0D;
    private static final int[] AGGRO_OBSTACLE_VERTICAL_SLICE_ORDER =
            new int[] {0, 3, 2, 1};

    // ---------------------------------------------------------------------
    // Sounds / animations
    // ---------------------------------------------------------------------

    private static final int MUMAKIL_STRIKE_ANIMATION_TICKS = 36;
    private static final byte MUMAKIL_STRIKE_LEFT_STATUS = 80;
    private static final byte MUMAKIL_STRIKE_RIGHT_STATUS = 81;
    private static final byte MUMAKIL_BABY_PANIC_START_STATUS = 82; // SLOW_PANIC_AND_PLAYER_RIDDEN_LOCOMOTION_V1
    private static final byte MUMAKIL_BABY_PANIC_STOP_STATUS = 83;

    // ---------------------------------------------------------------------
    // Idle yaw stabilization
    // ---------------------------------------------------------------------

    private static final float IDLE_YAW_SNAP_THRESHOLD = 45.0F;
    private static final float IDLE_YAW_MAX_STEP = 8.0F;
    private static final float IDLE_HEAD_YAW_LIMIT = 45.0F;
    private static final double IDLE_YAW_MOTION_THRESHOLD_SQ = 4.0E-4D;
    private static final float AI_MOVEMENT_MAX_TURN_RATE = 8.0F;
    private static final double AI_MOVEMENT_PATH_DIRECTION_MIN_SQ =
            0.25D;
    private static final double AI_MOVEMENT_MOTION_THRESHOLD_SQ =
            2.5E-3D;
    private static final double AI_CLOSE_COMBAT_FACING_RANGE_SQ =
            100.0D;

    // ---------------------------------------------------------------------
    // Mount inventory / data watcher
    // ---------------------------------------------------------------------

    private static final int HORSE_ARMOR_WATCHER_ID = 22;
    private static final int MOUNTED_DRIVER_HORN_TICKS_WATCHER_ID = 23;
    private static final int MUMAKIL_TRUMPET_TICKS_WATCHER_ID = 24;
    private static final int MUMAKIL_HOWDAH_SYNC_ARMOR_INDEX = 1;
    private static final String[] MUMAKIL_INVENTORY_FIELD_NAMES =
            new String[] {
                    "horseChest",
                    "field_110296_bG",
                    "bG",
                    "mountInventory",
                    "horseInventory",
                    "inventory"
            };
    private static volatile boolean mumakilInventoryFieldResolved;
    private static Field mumakilInventoryField;

    // ---------------------------------------------------------------------
    // Runtime state
    // ---------------------------------------------------------------------

    private int nextTrampleDamageTick;
    private final AnimationFactory animationFactory = new AnimationFactory(this);
    private final DriverTargetProgressState driverTargetProgressState = new DriverTargetProgressState();

    private MumakilMode mumakilMode;
    private MumakilFormationOrigin formationOrigin;
    private UUID hiredFormationOwnerUuid;
    private UUID mumakilInvasionId;
    private boolean authoritativeMumakilHowdahEquipped;
    private int howdahRosterLoadGraceTicks;
    private boolean naturalDespawnCleanupInProgress;
    private boolean naturalDespawnMembersRemoved;
    private boolean naturalSpawnSpacingBypassed;
    private Entity naturalDespawnCapturedDriver;
    private float mumakilSpeedTrait = NORMAL_SPEED_TRAIT;
    private boolean mumakilSpeedTraitInitialized;
    private boolean babyGrowthInitialized;
    private long babyMelonFedUntilTick;
    private long tamedBabyMelonHealUntilTick;
    private boolean captiveBred;
    private boolean babyLifecycleInitialized;
    private boolean babyPanicAnimationActive;
    private EntityPlayer lastMountedPlayerForSafeDismount; // ADULT_TAMING_BLOCK_SAFE_DISMOUNT_V1
    private EntityPlayer pendingSafeGroundDismountPlayer; // SAFE_DISMOUNT_NETWORK_RETRY_V2
    private int pendingSafeGroundDismountTicks;
    private boolean wasBabyMumakil;
    private float lastStableIdleYaw;
    private float lastStableIdleHeadYaw;
    private boolean hasStableIdleYaw;
    private float playerRiddenLocomotionPhase;
    private float prevPlayerRiddenLocomotionPhase;
    private float lastRawLocomotionPhase;
    private boolean playerRiddenLocomotionInitialized;
    private boolean wasPlayerRiddenForLocomotion;
    private int lastMumakilFootfallIndex;
    private boolean mumakilFootfallTrackingInitialized;
    private int lastMumakilFootfallTick = Integer.MIN_VALUE;

    private int debugSeatPlayerEntityId = -1;
    private long debugSeatLastSampleTick = Long.MIN_VALUE;
    private double debugSeatLastMumakX;
    private double debugSeatLastMumakZ;
    private double debugSeatTravelDistance;
    private boolean debugSeatLoggedFirstDistance;
    private boolean debugSeatLoggedSecondDistance;
    private int angerWaveCooldownTicks;
    private int angerWaveActiveTicks;
    private int territorialWarningTargetEntityId;
    private int territorialWarningTicksRemaining;
    private int[] territorialWarningMemoryTargetIds;
    private long[] territorialWarningMemoryUntil;
    private int territorialWarningMemoryCursor;
    private long nextWildHerdEventTick;
    private int lastWildHerdEventThreatEntityId;
    private long wildHerdRegroupUntilTick;
    private int wildHerdRegroupLeaderEntityId;
    private int wildHerdRegroupThreatEntityId;
    private double wildHerdRegroupX;
    private double wildHerdRegroupZ;
    private double wildHerdRegroupOffsetX;
    private double wildHerdRegroupOffsetZ;
    private int nextWildHerdRepathTick;
    private boolean wildHerdRegroupPathOwned;
    private int tuskAttackCooldownTicks;
    private int successfulTuskAttackSequence;
    private boolean mumakilAutonomousCombatPassActive;
    private int lastAggroObstacleClearTick = Integer.MIN_VALUE; // AGGRO_TREE_CORRIDOR_AND_WILD_BABY_FOLLOW_V1
    private int nextAggroObstacleSweepTick;
    private int aggroObstacleSliceIndex;
    private int aggroObstacleSliceLimit;
    private boolean aggroObstacleSweepBrokeBlocks;
    private int lastAggroObstacleRegionX = Integer.MIN_VALUE;
    private int lastAggroObstacleRegionY = Integer.MIN_VALUE;
    private int lastAggroObstacleRegionZ = Integer.MIN_VALUE;
    private int nextAggroObstacleProgressCheckTick;
    private double lastAggroObstacleProgressX;
    private double lastAggroObstacleProgressZ;
    private boolean hasAggroObstacleProgressSample;
    private IInventory cachedMumakilMountInventory;
    private boolean mumakilMountInventoryReadAttempted;
    private boolean mumakilHirePreview;
    private int recentFormationThreatEntityId;
    private long recentFormationThreatUntilTick;
    private int wildRetainedTargetEntityId = -1;
    private int wildTargetUnseenTicks;

    private int mumakilStrikeAnimationTicks;
    private int prevMumakilStrikeAnimationTicks;
    private boolean mumakilStrikeAnimationLeft;
    private boolean mumakilTrumpetSoundPlayed;
    private int mumakilAngrySoundTriggerCounter;

    public DriverTargetProgressState getDriverTargetProgressState() {
        return this.driverTargetProgressState;
    }

    public void recordRecentFormationThreat(EntityLivingBase attacker) {
        if (this.worldObj == null
                || this.worldObj.isRemote
                || attacker == null
                || attacker == this
                || !attacker.isEntityAlive()) {
            return;
        }

        this.recentFormationThreatEntityId = attacker.getEntityId();
        this.recentFormationThreatUntilTick =
                this.worldObj.getTotalWorldTime() + FORMATION_THREAT_MEMORY_TICKS;
    }

    public EntityLivingBase getRecentFormationThreat() {
        if (this.worldObj == null
                || this.recentFormationThreatEntityId <= 0
                || this.worldObj.getTotalWorldTime() >= this.recentFormationThreatUntilTick) {
            this.clearRecentFormationThreat();
            return null;
        }

        Entity entity = this.worldObj.getEntityByID(this.recentFormationThreatEntityId);
        if (!(entity instanceof EntityLivingBase)
                || !((EntityLivingBase)entity).isEntityAlive()) {
            this.clearRecentFormationThreat();
            return null;
        }

        return (EntityLivingBase)entity;
    }

    private void clearRecentFormationThreat() {
        this.recentFormationThreatEntityId = 0;
        this.recentFormationThreatUntilTick = 0L;
    }

    public static final class DriverTargetProgressState {
        public int progressTargetEntityId = -1;
        public long nextProgressCheckTick;
        public double lastProgressX;
        public double lastProgressY;
        public double lastProgressZ;
        public int stuckTicks;

        public void reset() {
            this.progressTargetEntityId = -1;
            this.nextProgressCheckTick = 0L;
            this.lastProgressX = 0.0D;
            this.lastProgressY = 0.0D;
            this.lastProgressZ = 0.0D;
            this.stuckTicks = 0;
        }
    }

    // ---------------------------------------------------------------------
    // Construction / GeckoLib / basic entity hooks
    // ---------------------------------------------------------------------

    public LOTREntityMumakil(World world) {
        super(world);
        // Main physical/hurt box. Baby dimensions are applied when the inherited age state becomes negative.
        this.setSize(ADULT_WIDTH, ADULT_HEIGHT);
        this.resetAngerWaveCooldown();
        this.replaceInheritedRiderTargetAI();
        this.replaceInheritedFollowHiringPlayerAI();
        this.replaceInheritedFollowParentAI();
        this.replaceInheritedMateAI();
        this.replaceInheritedHurtByTargetAI();
        this.removeInheritedPanicAI();

        this.tasks.addTask(2, new EntityAIMumakilBabyPanic());
        this.tasks.addTask(3, new EntityAIMumakilFollowMelon());
        this.tasks.addTask(4, new EntityAIMumakilBabyFollowAdult());
        this.tasks.addTask(5, new EntityAIWildMumakilMove());
        this.tasks.addTask(6, new EntityAIBlockHiredWarWander());
        /*
         * Spontaneous territorial selection is owned by the bounded warning
         * scan. The inherited hurt-by-target replacement remains responsible
         * for immediate retaliation.
         */
    }

    @Override
    protected void entityInit() {
        super.entityInit();
        this.dataWatcher.addObject(
                MOUNTED_DRIVER_HORN_TICKS_WATCHER_ID,
                Integer.valueOf(0)
        );
        this.dataWatcher.addObject(
                MUMAKIL_TRUMPET_TICKS_WATCHER_ID,
                Integer.valueOf(0)
        );
    }

    public int getMountedDriverHornTicks() {
        return Math.max(
                0,
                this.dataWatcher.getWatchableObjectInt(
                        MOUNTED_DRIVER_HORN_TICKS_WATCHER_ID
                )
        );
    }

    public void setMountedDriverHornTicks(int ticks) {
        if (this.worldObj != null && !this.worldObj.isRemote) {
            this.dataWatcher.updateObject(
                    MOUNTED_DRIVER_HORN_TICKS_WATCHER_ID,
                    Integer.valueOf(Math.max(0, ticks))
            );
        }
    }

    public int getMumakilTrumpetAnimationTicks() {
        return Math.max(
                0,
                this.dataWatcher.getWatchableObjectInt(
                        MUMAKIL_TRUMPET_TICKS_WATCHER_ID
                )
        );
    }

    public float getMumakilTrumpetAnimationProgress(
            float partialTicks
    ) {
        int remaining = this.getMumakilTrumpetAnimationTicks();
        if (remaining <= 0) {
            return -1.0F;
        }

        float elapsed = MUMAKIL_TRUMPET_ANIMATION_TICKS
                - remaining
                + MathHelper.clamp_float(
                partialTicks,
                0.0F,
                1.0F
        );
        return MathHelper.clamp_float(
                elapsed
                        / (float)MUMAKIL_TRUMPET_ANIMATION_TICKS,
                0.0F,
                1.0F
        );
    }

    public boolean isMumakilTrumpetAnimationActive() {
        return this.getMumakilTrumpetAnimationTicks() > 0;
    }

    private void setMumakilTrumpetAnimationTicks(int ticks) {
        if (this.worldObj != null && !this.worldObj.isRemote) {
            this.dataWatcher.updateObject(
                    MUMAKIL_TRUMPET_TICKS_WATCHER_ID,
                    Integer.valueOf(Math.max(0, ticks))
            );
        }
    }

    private void replaceInheritedRiderTargetAI() {
        EntityAITasks.EntityAITaskEntry inherited = LOTREntityUtils.removeAITask(
                this,
                LOTREntityAIHorseMoveToRiderTarget.class
        );

        if (inherited != null) {
            this.tasks.addTask(inherited.priority, new EntityAIMumakilMoveToRiderTarget());
        }
    }

    /**
     * MiddleEarth Tweaks replaces the rider-target task after construction in
     * its server-side EntityJoinWorldEvent handler. Restore only this Mumak's
     * task after that handler has run, without linking against Tweaks.
     */
    public void restoreMumakilRiderTargetAICompatibility() {
        List<EntityAITasks.EntityAITaskEntry> riderTargetEntries =
                new ArrayList<EntityAITasks.EntityAITaskEntry>();
        int priority = Integer.MAX_VALUE;

        for (Object value : this.tasks.taskEntries) {
            EntityAITasks.EntityAITaskEntry entry =
                    (EntityAITasks.EntityAITaskEntry)value;
            EntityAIBase action = entry.action;
            if (action instanceof EntityAIMumakilMoveToRiderTarget
                    || isMiddleEarthTweaksRiderTargetAI(action)) {
                riderTargetEntries.add(entry);
                priority = Math.min(priority, entry.priority);
            }
        }

        if (!riderTargetEntries.isEmpty()) {
            for (EntityAITasks.EntityAITaskEntry entry : riderTargetEntries) {
                this.tasks.removeTask(entry.action);
            }
            this.tasks.addTask(
                    priority,
                    new EntityAIMumakilMoveToRiderTarget()
            );
        }
    }

    private static boolean isMiddleEarthTweaksRiderTargetAI(
            EntityAIBase action
    ) {
        if (action == null) {
            return false;
        }

        Class<?> type = action.getClass();
        while (type != null) {
            if (MIDDLE_EARTH_TWEAKS_RIDER_TARGET_AI_CLASS.equals(
                    type.getName()
            )) {
                return true;
            }
            type = type.getSuperclass();
        }
        return false;
    }

    private void removeInheritedPanicAI() {
        while (LOTREntityUtils.removeAITask(
                this,
                EntityAIPanic.class
        ) != null) {
            // Remove every inherited horse panic task before installing
            // the Mumakil-specific baby panic task.
        }
    }

    private void replaceInheritedFollowHiringPlayerAI() {
        EntityAITasks.EntityAITaskEntry inherited = LOTREntityUtils.removeAITask(
                this,
                LOTREntityAIHorseFollowHiringPlayer.class
        );

        if (inherited != null) {
            this.tasks.addTask(inherited.priority, new EntityAIMumakilFollowHiringPlayer());
        }
    }

    private void replaceInheritedHurtByTargetAI() {
        EntityAITasks.EntityAITaskEntry inherited = LOTREntityUtils.removeAITask(
                this,
                EntityAIHurtByTarget.class
        );

        if (inherited != null) {
            this.targetTasks.addTask(inherited.priority, new EntityAIMumakilHurtByTarget());
        }
    }

    private void replaceInheritedFollowParentAI() {
        /*
         * LOTREntityHorse inherits the standard animal follow-parent task.
         * That task tries to move babies directly beside the adult and conflicts
         * with the wider Mumakil family roaming radius.
         *
         * Our EntityAIMumakilBabyFollowAdult task now owns this behavior.
         */
        LOTREntityUtils.removeAITask(
                this,
                EntityAIFollowParent.class
        );
    }


    /**
     * MUMAK_WIDE_BODY_BREEDING_AI_FIX_V1
     *
     * Vanilla EntityAIMate requires the parents' centers to become less than
     * three blocks apart. Adult Mumak are seven blocks wide, so their bodies
     * collide long before that condition can be met. Replace only the mating
     * task with a width-aware version; all other movement ownership remains
     * unchanged.
     */
    private void replaceInheritedMateAI() {
        EntityAITasks.EntityAITaskEntry inherited =
                LOTREntityUtils.removeAITask(
                        this,
                        EntityAIMate.class
                );

        int priority = inherited == null ? 2 : inherited.priority;
        this.tasks.addTask(priority, new EntityAIMumakilMate());
    }
    public float getCollisionBorderSize() {
        // Scale the extra melee/raycast padding with the real physical body size.
        return this.isChild() ? 1.25F * BABY_RENDER_SCALE : 1.25F;
    }

    @Override
    public void setScaleForAge(boolean child) {
        /*
         * Disable EntityAgeable's automatic child hitbox scaling.
         * Mumakil dimensions are controlled by applyMumakilPhysicalSize().
         */
        super.setScaleForAge(false);
    }

    public void registerControllers(AnimationData data) {
    }

    public AnimationFactory getFactory() {
        return this.animationFactory;
    }

    public int getHorseType() {
        return 0;
    }

    @Override
    public IEntityLivingData onSpawnWithEgg(IEntityLivingData spawnData) {
        /*
         * MUMAKIL_NATURAL_SPAWNING_V1
         *
         * Let LOTREntityHorse perform its normal spawn initialization, but
         * keep Mumakil-specific group data so one natural pack can contain
         * adults and rare babies. Spawn eggs create only the first adult
         * because they do not continue a shared pack.
         */
        super.onSpawnWithEgg(null);
        this.initializeRandomSpeedTraitIfNeeded();

        MumakilHerdSpawnData herdData;
        if (spawnData instanceof MumakilHerdSpawnData) {
            herdData = (MumakilHerdSpawnData)spawnData;
        } else {
            int plannedBabyCount = 0;
            if (this.rand.nextInt(NATURAL_HERD_FAMILY_CHANCE) == 0) {
                plannedBabyCount = 1 + this.rand.nextInt(
                        NATURAL_HERD_MAX_BABIES
                );
            }

            herdData = new MumakilHerdSpawnData(plannedBabyCount);
        }

        boolean firstHerdMember = herdData.memberIndex == 0;
        /*
         * Vanilla spawn eggs invoke onSpawnWithEgg before inserting the
         * entity into the world. Natural runtime and world-generation
         * spawning insert it first. Use that transient lifecycle distinction
         * so a manually spawned adult is never mistaken for a natural herd
         * seed, while the 128-block rule remains unchanged for both natural
         * spawning paths.
         */
        boolean naturalSpawnLifecycle =
                this.worldObj.loadedEntityList.contains(this);
        if (firstHerdMember
                && !this.worldObj.isRemote
                && naturalSpawnLifecycle
                && !this.naturalSpawnSpacingBypassed
                && this.hasNearbyWildMumakil(
                NATURAL_HERD_SPACING_RADIUS
        )) {
            herdData.spacingRejected = true;
        }

        if (herdData.spacingRejected) {
            this.setDead();
            return herdData;
        }

        boolean spawnAsBaby =
                herdData.memberIndex
                        >= NATURAL_HERD_ADULTS_BEFORE_BABIES
                        && herdData.babiesSpawned
                        < herdData.plannedBabyCount;

        if (spawnAsBaby) {
            this.setGrowingAge(-BABY_GROWTH_TICKS);
            this.babyGrowthInitialized = true;
            this.setMumakilMode(MumakilMode.BABY_WILD);
            ++herdData.babiesSpawned;
        } else {
            this.setGrowingAge(0);
            this.babyGrowthInitialized = false;
            this.setMumakilMode(MumakilMode.ADULT_WILD);
        }

        this.applyMumakilLifecycleState(spawnAsBaby, false);
        this.babyLifecycleInitialized = true;
        this.wasBabyMumakil = spawnAsBaby;
        this.setHealth(this.getMaxHealth());

        if (!this.worldObj.isRemote) {
            if (firstHerdMember) {
                herdData.centerX = this.posX;
                herdData.centerZ = this.posZ;
            } else {
                this.placeNaturalHerdMember(herdData);
            }
            herdData.members.add(this);
        }

        ++herdData.memberIndex;
        return herdData;
    }

    /**
     * Marks this entity as a non-natural creation path. The flag is transient
     * and exists only because vanilla does not identify the source of the
     * entity before calling onSpawnWithEgg(null).
     */
    public void bypassNaturalSpawnSpacing() {
        this.naturalSpawnSpacingBypassed = true;
    }

    private boolean hasNearbyWildMumakil(double radius) {
        AxisAlignedBB area = this.boundingBox.expand(radius, radius, radius);
        List nearby = this.worldObj.getEntitiesWithinAABB(
                LOTREntityMumakil.class,
                area
        );
        double radiusSq = radius * radius;

        for (int i = 0; i < nearby.size(); ++i) {
            LOTREntityMumakil other =
                    (LOTREntityMumakil)nearby.get(i);
            if (other == this
                    || other.isDead
                    || !other.isEntityAlive()
                    || !other.isWildMumakil()) {
                continue;
            }

            double dx = other.posX - this.posX;
            double dz = other.posZ - this.posZ;
            if (dx * dx + dz * dz <= radiusSq) {
                return true;
            }
        }

        return false;
    }

    private void placeNaturalHerdMember(MumakilHerdSpawnData herdData) {
        for (int attempt = 0;
                attempt < NATURAL_HERD_MEMBER_POSITION_ATTEMPTS;
                ++attempt) {
            double angle = this.rand.nextDouble() * Math.PI * 2.0D;
            double radius = NATURAL_HERD_MEMBER_MIN_RADIUS
                    + this.rand.nextDouble()
                    * (NATURAL_HERD_MEMBER_MAX_RADIUS
                    - NATURAL_HERD_MEMBER_MIN_RADIUS);
            double candidateX = herdData.centerX
                    + Math.cos(angle) * radius;
            double candidateZ = herdData.centerZ
                    + Math.sin(angle) * radius;
            int blockX = MathHelper.floor_double(candidateX);
            int blockZ = MathHelper.floor_double(candidateZ);

            if (!this.worldObj.blockExists(blockX, 0, blockZ)) {
                continue;
            }

            int groundY = this.worldObj.getTopSolidOrLiquidBlock(
                    blockX,
                    blockZ
            );
            if (!this.worldObj.blockExists(blockX, groundY, blockZ)
                    || !SpawnerAnimals.canCreatureTypeSpawnAtLocation(
                    EnumCreatureType.creature,
                    this.worldObj,
                    blockX,
                    groundY,
                    blockZ
            )) {
                continue;
            }

            AxisAlignedBB candidateBox = AxisAlignedBB.getBoundingBox(
                    candidateX - this.width * 0.5D,
                    groundY,
                    candidateZ - this.width * 0.5D,
                    candidateX + this.width * 0.5D,
                    groundY + this.height,
                    candidateZ + this.width * 0.5D
            );
            if (this.worldObj.isAnyLiquid(candidateBox)
                    || !this.worldObj.getCollidingBoundingBoxes(
                    this,
                    candidateBox
            ).isEmpty()
                    || this.overlapsNaturalHerdMember(
                    herdData,
                    candidateX,
                    candidateZ
            )) {
                continue;
            }

            this.setPosition(candidateX, groundY, candidateZ);
            this.prevPosX = candidateX;
            this.prevPosY = groundY;
            this.prevPosZ = candidateZ;
            return;
        }
    }

    private boolean overlapsNaturalHerdMember(
            MumakilHerdSpawnData herdData,
            double candidateX,
            double candidateZ
    ) {
        for (int i = 0; i < herdData.members.size(); ++i) {
            LOTREntityMumakil other =
                    (LOTREntityMumakil)herdData.members.get(i);
            double minimumDistance =
                    (this.width + other.width) * 0.5D + 1.0D;
            double dx = candidateX - other.posX;
            double dz = candidateZ - other.posZ;
            if (dx * dx + dz * dz
                    < minimumDistance * minimumDistance) {
                return true;
            }
        }
        return false;
    }


    /**
     * MUMAKIL_CALF_SPAWN_EGG_V1
     *
     * Converts a freshly created normal Mumakil entity into the exact same
     * wild-baby lifecycle used by naturally spawned calves. Keeping the
     * normal entity class means the calf grows into an ordinary Mumakil and
     * remains compatible with the existing renderer, AI, taming, and breeding.
     */
    public void initializeAsSpawnEggCalf() {
        this.initializeRandomSpeedTraitIfNeeded();
        this.setBelongsToNPC(false);
        this.setHorseTamed(false);
        this.setGrowingAge(-BABY_GROWTH_TICKS);

        this.babyGrowthInitialized = true;
        this.babyMelonFedUntilTick = 0L;
        this.tamedBabyMelonHealUntilTick = 0L;

        this.setMumakilMode(MumakilMode.BABY_WILD);
        this.applyMumakilLifecycleState(true, false);

        this.babyLifecycleInitialized = true;
        this.wasBabyMumakil = true;
        this.setHealth(this.getMaxHealth());
    }
    @Override
    public boolean canDespawn() {
        /*
         * Wild herds and autonomous natural war formations use normal distance
         * despawning. Player-hired, tamed, and other NPC-owned Mumak remain
         * persistent.
         */
        return this.isWildMumakil()
                || this.isNaturalNearHaradFormation();
    }

    @Override
    protected void despawnEntity() {
        boolean naturalFormation = this.isNaturalNearHaradFormation();
        Entity capturedDriver = this.riddenByEntity;
        if (!naturalFormation) {
            super.despawnEntity();
            return;
        }

        this.naturalDespawnCleanupInProgress = true;
        this.naturalDespawnMembersRemoved = false;
        this.naturalDespawnCapturedDriver = capturedDriver;
        try {
            super.despawnEntity();
        } finally {
            this.naturalDespawnCleanupInProgress = false;
            if (this.isDead && !this.naturalDespawnMembersRemoved) {
                MumakilWarFormationFactory.removeNaturalFormationMembers(
                        this,
                        this.naturalDespawnCapturedDriver
                );
                this.naturalDespawnMembersRemoved = true;
            }
            this.naturalDespawnCapturedDriver = null;
        }
    }

    @Override
    public void setDead() {
        boolean wasDead = this.isDead;
        super.setDead();

        if (!wasDead
                && this.isDead
                && this.naturalDespawnCleanupInProgress
                && !this.naturalDespawnMembersRemoved) {
            MumakilWarFormationFactory.removeNaturalFormationMembers(
                    this,
                    this.naturalDespawnCapturedDriver
            );
            this.naturalDespawnMembersRemoved = true;
        }
    }

    private static final class MumakilHerdSpawnData
            implements IEntityLivingData {
        private final int plannedBabyCount;
        private final List members = new ArrayList();
        private double centerX;
        private double centerZ;
        private int memberIndex;
        private int babiesSpawned;
        private boolean spacingRejected;

        private MumakilHerdSpawnData(int plannedBabyCount) {
            this.plannedBabyCount = Math.max(
                    0,
                    Math.min(
                            NATURAL_HERD_MAX_BABIES,
                            plannedBabyCount
                    )
            );
        }
    }


    // ---------------------------------------------------------------------
    // Wild and hired-war state gates
    // ---------------------------------------------------------------------

    public MumakilMode getMumakilMode() {
        if (this.mumakilMode == null) {
            this.mumakilMode = this.getEntityData().getBoolean(NBT_HIRED_WAR_MUMAKIL)
                    ? MumakilMode.HIRED_WAR
                    : this.inferNonHiredMumakilMode();
        } else if (this.mumakilMode != MumakilMode.HIRED_WAR) {
            MumakilMode inferredMode = this.inferNonHiredMumakilMode();
            if (this.mumakilMode != inferredMode) {
                this.mumakilMode = inferredMode;
            }
        }

        return this.mumakilMode;
    }

    public void setMumakilMode(MumakilMode mode) {
        if (mode == null) {
            mode = this.inferNonHiredMumakilMode();
        }

        this.mumakilMode = mode;
        this.getEntityData().setBoolean(
                NBT_HIRED_WAR_MUMAKIL,
                mode == MumakilMode.HIRED_WAR
        );
    }

    private MumakilMode inferNonHiredMumakilMode() {
        if (this.isChild()) {
            return this.isTame() ? MumakilMode.BABY_TAMED : MumakilMode.BABY_WILD;
        }

        return this.isTame() ? MumakilMode.ADULT_TAMED : MumakilMode.ADULT_WILD;
    }

    public boolean isHiredWarMumakil() {
        return this.getMumakilMode() == MumakilMode.HIRED_WAR;
    }

    public void setHiredWarMumakil(boolean hiredWar) {
        this.setMumakilMode(
                hiredWar ? MumakilMode.HIRED_WAR : this.inferNonHiredMumakilMode()
        );
    }

    public MumakilFormationOrigin getFormationOrigin() {
        if (this.formationOrigin == null) {
            /*
             * Old saves predate the origin key. Every pre-existing hired-war
             * Mumak was player-hired, so that is the backwards-compatible
             * inference.
             */
            this.formationOrigin = this.isHiredWarMumakil()
                    ? MumakilFormationOrigin.PLAYER_HIRED
                    : MumakilFormationOrigin.NONE;
        }
        return this.formationOrigin;
    }

    public void setFormationOrigin(MumakilFormationOrigin origin) {
        this.formationOrigin = origin == null
                ? MumakilFormationOrigin.NONE
                : origin;
    }

    public boolean isAutonomousWarFormation() {
        MumakilFormationOrigin origin = this.getFormationOrigin();
        return origin == MumakilFormationOrigin.NATURAL_NEAR_HARAD
                || origin == MumakilFormationOrigin.CONQUEST_NEAR_HARAD
                || origin == MumakilFormationOrigin.INVASION_NEAR_HARAD
                || origin == MumakilFormationOrigin.CREATIVE_SPAWN_EGG;
    }

    public boolean isWarCombatFormation() {
        return this.isAutonomousWarFormation()
                || this.getFormationOrigin()
                == MumakilFormationOrigin.PLAYER_HIRED;
    }

    public boolean isAutonomousCombatPassActive() {
        return this.isWarCombatFormation()
                && this.mumakilAutonomousCombatPassActive;
    }

    public int getTuskAttackCooldownTicks() {
        return Math.max(0, this.tuskAttackCooldownTicks);
    }

    public void capturePlayerHiredFormationOwner(LOTREntityNPC driver) {
        if (this.worldObj == null
                || this.worldObj.isRemote
                || this.getFormationOrigin()
                != MumakilFormationOrigin.PLAYER_HIRED
                || driver == null
                || !driver.hiredNPCInfo.isActive) {
            return;
        }

        UUID ownerUuid = driver.hiredNPCInfo.getHiringPlayerUUID();
        if (ownerUuid != null) {
            this.hiredFormationOwnerUuid = ownerUuid;
        }
    }

    public UUID getPlayerHiredFormationOwnerUuid() {
        return this.getFormationOrigin()
                == MumakilFormationOrigin.PLAYER_HIRED
                ? this.hiredFormationOwnerUuid
                : null;
    }

    public EntityPlayer getOnlinePlayerHiredFormationOwner() {
        UUID ownerUuid = this.getPlayerHiredFormationOwnerUuid();
        return ownerUuid == null || this.worldObj == null
                ? null
                : this.worldObj.func_152378_a(ownerUuid);
    }

    public void setMumakilInvasionId(UUID invasionId) {
        this.mumakilInvasionId = invasionId;
    }

    public UUID getMumakilInvasionId() {
        return this.getFormationOrigin()
                == MumakilFormationOrigin.INVASION_NEAR_HARAD
                ? this.mumakilInvasionId
                : null;
    }

    public boolean isNaturalNearHaradFormation() {
        MumakilFormationOrigin origin = this.getFormationOrigin();
        return origin == MumakilFormationOrigin.NATURAL_NEAR_HARAD
                || origin == MumakilFormationOrigin.CONQUEST_NEAR_HARAD;
    }

    public boolean isBabyMumakil() {
        return this.getMumakilMode().isBaby();
    }

    public boolean isAdultMumakil() {
        return this.getMumakilMode().isAdult();
    }

    public float getMumakilSpeedTrait() {
        return clampSpeedTrait(
                this.mumakilSpeedTraitInitialized
                        ? this.mumakilSpeedTrait
                        : NORMAL_SPEED_TRAIT
        );
    }

    private void initializeRandomSpeedTraitIfNeeded() {
        if (this.mumakilSpeedTraitInitialized) {
            return;
        }

        /*
         * The mean of two uniform samples is triangular and center-weighted:
         * most new Mumak remain close to 1.00 while 0.85/1.15 are rare.
         */
        float centeredSample =
                (this.rand.nextFloat() + this.rand.nextFloat()) * 0.5F;
        this.setMumakilSpeedTrait(
                MIN_SPEED_TRAIT
                        + centeredSample
                        * (MAX_SPEED_TRAIT - MIN_SPEED_TRAIT)
        );
    }

    private void setMumakilSpeedTrait(float speedTrait) {
        this.mumakilSpeedTrait = clampSpeedTrait(speedTrait);
        this.mumakilSpeedTraitInitialized = true;
        this.applyConfiguredMovementSpeed();
    }

    private static float clampSpeedTrait(float speedTrait) {
        if (Float.isNaN(speedTrait) || Float.isInfinite(speedTrait)) {
            return NORMAL_SPEED_TRAIT;
        }
        return MathHelper.clamp_float(
                speedTrait,
                MIN_SPEED_TRAIT,
                MAX_SPEED_TRAIT
        );
    }

    public boolean isTamedMumakilMode() {
        return this.getMumakilMode().isTamed();
    }

    public float getMumakilRenderScale() {
        return this.shouldUseBabyLifecycleState() ? BABY_RENDER_SCALE : 1.0F;
    }

    private boolean shouldUseBabyLifecycleState() {
        return this.isChild() && !this.isHiredWarMumakil();
    }

    private void initializeBabyGrowthTimerIfNeeded() {
        if (this.shouldUseBabyLifecycleState() && !this.babyGrowthInitialized) {
            this.setGrowingAge(-BABY_GROWTH_TICKS);
            this.babyGrowthInitialized = true;
        }
    }

    private void updateMumakilLifecycle() {
        this.initializeBabyGrowthTimerIfNeeded();

        boolean baby = this.shouldUseBabyLifecycleState();
        if (!this.babyLifecycleInitialized) {
            this.applyMumakilLifecycleState(baby, false);
            this.babyLifecycleInitialized = true;
            this.wasBabyMumakil = baby;
            return;
        }

        if (baby != this.wasBabyMumakil) {
            if (!this.isHiredWarMumakil()) {
                this.setMumakilMode(
                        baby
                                ? (this.isTame() ? MumakilMode.BABY_TAMED : MumakilMode.BABY_WILD)
                                : (this.isTame() ? MumakilMode.ADULT_TAMED : MumakilMode.ADULT_WILD)
                );
            }

            this.applyMumakilLifecycleState(baby, true);
            if (!baby) {
                this.babyGrowthInitialized = false;
            }
            this.wasBabyMumakil = baby;
        } else {
            this.applyMumakilPhysicalSize(baby);
        }
    }

    private void applyMumakilLifecycleState(boolean baby, boolean preserveHealthRatio) {
        double healthRatio = 1.0D;
        if (preserveHealthRatio) {
            double currentMaxHealth = Math.max(1.0D, this.getMaxHealth());
            healthRatio = MathHelper.clamp_double(
                    this.getHealth() / currentMaxHealth,
                    0.0D,
                    1.0D
            );
        }

        this.applyMumakilPhysicalSize(baby);
        this.applyConfiguredAttributes();

        if (preserveHealthRatio) {
            this.setHealth((float)(healthRatio * this.getMaxHealth()));
        } else if (this.getHealth() > this.getMaxHealth()) {
            this.setHealth(this.getMaxHealth());
        }
    }

    private void applyMumakilPhysicalSize(boolean baby) {
        float targetWidth = baby ? BABY_WIDTH : ADULT_WIDTH;
        float targetHeight = baby ? BABY_HEIGHT : ADULT_HEIGHT;

        if (Math.abs(this.width - targetWidth) > 0.001F
                || Math.abs(this.height - targetHeight) > 0.001F) {
            this.setSize(targetWidth, targetHeight);

            /*
             * EntityAgeable#setSize updates its stored base dimensions, but it may
             * not immediately update the actual collision box after initialization.
             * Force the stored dimensions to be applied at a 1.0 scale.
             */
            this.setScaleForAge(false);
        }
    }

    private boolean isWildMumakil() {
        MumakilMode mode = this.getMumakilMode();
        return (mode == MumakilMode.BABY_WILD || mode == MumakilMode.ADULT_WILD)
                && !this.getBelongsToNPC()
                && this.riddenByEntity == null;
    }
    private boolean isWildAdultMumakil() {
        return this.getMumakilMode() == MumakilMode.ADULT_WILD
                && !this.getBelongsToNPC()
                && this.riddenByEntity == null;
    }

    private boolean shouldWildWander() {
        return this.isWildMumakil()
                && !this.getBelongsToNPC()
                && !this.hasMumakilHowdahEquipped()
                && !this.isMountEnraged()
                && !this.isTerritorialWarningActive()
                && !this.isWildHerdRegroupActive()
                && this.getAttackTarget() == null;
    }

    private boolean shouldWildChaseTarget() {
        EntityLivingBase target = this.getAttackTarget();
        return this.isWildAdultMumakil()
                && !this.hasMumakilHowdahEquipped()
                && target != null
                && this.canTuskAttackTarget(target);
    }


    // ---------------------------------------------------------------------
    // Wild target selection and movement AI
    // ---------------------------------------------------------------------

    private void validateWildAttackTargetLifecycle() {
        if (!this.isWildAdultMumakil()) {
            this.wildRetainedTargetEntityId = -1;
            this.wildTargetUnseenTicks = 0;
            return;
        }

        EntityLivingBase target = this.getAttackTarget();
        if (target == null) {
            this.wildRetainedTargetEntityId = -1;
            this.wildTargetUnseenTicks = 0;
            return;
        }

        boolean present = !target.isDead
                && target.isEntityAlive()
                && target.worldObj == this.worldObj
                && target.getEntityId() > 0
                && this.worldObj.getEntityByID(target.getEntityId()) == target;
        boolean inRange = present
                && this.getDistanceSqToEntity(target)
                <= WILD_TARGET_RETENTION_RANGE
                * WILD_TARGET_RETENTION_RANGE;
        boolean legal = present && this.canTuskAttackTarget(target);

        if (!present || !inRange || !legal) {
            this.setAttackTarget(null);
            if (this.getAITarget() == target) {
                this.setRevengeTarget(null);
            }
            this.wildRetainedTargetEntityId = -1;
            this.wildTargetUnseenTicks = 0;
            return;
        }

        int targetId = target.getEntityId();
        if (this.wildRetainedTargetEntityId != targetId) {
            this.wildRetainedTargetEntityId = targetId;
            this.wildTargetUnseenTicks = 0;
        }

        if (this.getEntitySenses().canSee(target)) {
            this.wildTargetUnseenTicks = 0;
        } else if (++this.wildTargetUnseenTicks
                > WILD_TARGET_UNSEEN_MEMORY_TICKS) {
            this.setAttackTarget(null);
            if (this.getAITarget() == target) {
                this.setRevengeTarget(null);
            }
            this.wildRetainedTargetEntityId = -1;
            this.wildTargetUnseenTicks = 0;
        }
    }

    private void tryAcquireWildMobTarget() {
        if (!this.isWildAdultMumakil()
                || !this.isWildAngerWaveActive()
                || this.getAttackTarget() != null
                || this.isTerritorialWarningActive()
                || this.isWildHerdRegroupActive()
                || this.ticksExisted % MOB_TARGET_CHECK_INTERVAL != 0) {
            return;
        }

        boolean trackPerformance =
                MumakilPerformanceTracker.isEnabled();
        long perfStart = trackPerformance
                ? MumakilPerformanceTracker.startTimer()
                : 0L;
        List nearby = this.worldObj.getEntitiesWithinAABB(
                EntityLivingBase.class,
                this.boundingBox.expand(MOB_TARGET_RANGE, MOB_TARGET_VERTICAL_RANGE, MOB_TARGET_RANGE)
        );

        EntityLivingBase bestTarget = null;
        int bestPriority = Integer.MAX_VALUE;
        double bestDistanceSq = Double.MAX_VALUE;

        for(int i = 0; i < nearby.size(); ++i) {
            EntityLivingBase candidate = (EntityLivingBase)nearby.get(i);
            if (!this.canWarnTerritorialTarget(candidate)
                    || this.isTerritorialWarningTargetRemembered(
                    candidate
            )) {
                continue;
            }

            int priority = this.getWildMobTargetPriority(candidate);
            double distanceSq = this.getDistanceSqToEntity(candidate);
            if (distanceSq > TERRITORIAL_WARNING_RADIUS
                    * TERRITORIAL_WARNING_RADIUS) {
                continue;
            }

            if (priority < bestPriority || priority == bestPriority && distanceSq < bestDistanceSq) {
                bestTarget = candidate;
                bestPriority = priority;
                bestDistanceSq = distanceSq;
            }
        }

        if (trackPerformance) {
            MumakilPerformanceTracker.recordMountTargetScan(this, nearby.size(), System.nanoTime() - perfStart);
        }

        if (bestTarget != null
                && (bestPriority < 2 || this.rand.nextInt(4) == 0)) {
            if (bestDistanceSq <=
                    TERRITORIAL_IMMEDIATE_AGGRESSION_RADIUS
                            * TERRITORIAL_IMMEDIATE_AGGRESSION_RADIUS) {
                this.rememberTerritorialWarningTarget(bestTarget);
                this.playMumakilAngrySound();
                this.setAttackTarget(bestTarget);
                return;
            }

            this.beginTerritorialWarning(bestTarget);
        }
    }

    private boolean canWarnTerritorialTarget(
            EntityLivingBase target
    ) {
        if (target instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer)target;
            return player.isEntityAlive()
                    && !player.capabilities.isCreativeMode
                    && !this.isPlayerHoldingMelon(player)
                    && this.canTuskAttackTarget(player);
        }
        return this.canTargetWildMob(target);
    }

    private void beginTerritorialWarning(EntityLivingBase target) {
        if (target == null || !this.canWarnTerritorialTarget(target)) {
            return;
        }

        this.territorialWarningTargetEntityId = target.getEntityId();
        this.territorialWarningTicksRemaining =
                TERRITORIAL_WARNING_DURATION_TICKS;
        this.getNavigator().clearPathEntity();
        this.startMumakilTrumpetSequenceNow();
        this.triggerWildHerdRegroup(target, false);
        this.faceWildMovePoint(target.posX, target.posZ);
    }

    private void updateTerritorialWarningAndHerdRegroup() {
        this.updateTerritorialWarning();
        this.updateWildHerdRegroup();
    }

    private void updateTerritorialWarning() {
        if (!this.isTerritorialWarningActive()) {
            return;
        }

        if (!this.isWildAdultMumakil()) {
            this.clearTerritorialWarning(false);
            return;
        }

        Entity entity = this.worldObj.getEntityByID(
                this.territorialWarningTargetEntityId
        );
        if (!(entity instanceof EntityLivingBase)
                || !this.canWarnTerritorialTarget(
                (EntityLivingBase)entity
        )) {
            this.clearTerritorialWarning(true);
            return;
        }

        EntityLivingBase target = (EntityLivingBase)entity;
        EntityLivingBase urgentTarget = this.getAttackTarget();
        if (urgentTarget != null) {
            this.clearTerritorialWarning(
                    urgentTarget != target
            );
            return;
        }

        double distanceSq = this.getDistanceSqToEntity(target);
        if (distanceSq
                > TERRITORIAL_WARNING_CANCEL_RADIUS
                * TERRITORIAL_WARNING_CANCEL_RADIUS) {
            this.clearTerritorialWarning(true);
            return;
        }

        this.getNavigator().clearPathEntity();
        this.faceWildMovePoint(target.posX, target.posZ);

        if (distanceSq
                <= TERRITORIAL_IMMEDIATE_AGGRESSION_RADIUS
                * TERRITORIAL_IMMEDIATE_AGGRESSION_RADIUS) {
            this.rememberTerritorialWarningTarget(target);
            this.playMumakilAngrySound();
            this.clearTerritorialWarning(false);
            this.setAttackTarget(target);
            return;
        }

        --this.territorialWarningTicksRemaining;
        if (this.territorialWarningTicksRemaining <= 0) {
            this.rememberTerritorialWarningTarget(target);
            this.clearTerritorialWarning(false);
            this.setAttackTarget(target);
        }
    }

    private boolean isTerritorialWarningActive() {
        return this.territorialWarningTargetEntityId != 0
                && this.territorialWarningTicksRemaining > 0;
    }

    private void rememberTerritorialWarningTarget(
            EntityLivingBase target
    ) {
        if (target == null) {
            return;
        }
        if (this.territorialWarningMemoryTargetIds == null) {
            this.territorialWarningMemoryTargetIds =
                    new int[TERRITORIAL_WARNING_MEMORY_SLOTS];
            this.territorialWarningMemoryUntil =
                    new long[TERRITORIAL_WARNING_MEMORY_SLOTS];
        }

        int targetId = target.getEntityId();
        int slot = -1;
        for (int i = 0;
             i < this.territorialWarningMemoryTargetIds.length;
             ++i) {
            if (this.territorialWarningMemoryTargetIds[i]
                    == targetId) {
                slot = i;
                break;
            }
        }
        if (slot < 0) {
            slot = this.territorialWarningMemoryCursor;
            this.territorialWarningMemoryCursor =
                    (this.territorialWarningMemoryCursor + 1)
                            % TERRITORIAL_WARNING_MEMORY_SLOTS;
        }
        this.territorialWarningMemoryTargetIds[slot] = targetId;
        this.territorialWarningMemoryUntil[slot] =
                this.worldObj.getTotalWorldTime()
                        + TERRITORIAL_WARNING_MEMORY_TICKS;
    }

    private boolean isTerritorialWarningTargetRemembered(
            EntityLivingBase target
    ) {
        if (target == null
                || this.territorialWarningMemoryTargetIds == null) {
            return false;
        }
        int targetId = target.getEntityId();
        long now = this.worldObj.getTotalWorldTime();
        for (int i = 0;
             i < this.territorialWarningMemoryTargetIds.length;
             ++i) {
            if (this.territorialWarningMemoryTargetIds[i]
                    == targetId
                    && now < this.territorialWarningMemoryUntil[i]) {
                return true;
            }
        }
        return false;
    }

    private void clearTerritorialWarning(boolean rememberTarget) {
        if (rememberTarget) {
            Entity entity = this.worldObj.getEntityByID(
                    this.territorialWarningTargetEntityId
            );
            if (entity instanceof EntityLivingBase) {
                this.rememberTerritorialWarningTarget(
                        (EntityLivingBase)entity
                );
            }
        }
        this.territorialWarningTargetEntityId = 0;
        this.territorialWarningTicksRemaining = 0;
    }

    private void triggerWildHerdRegroup(
            EntityLivingBase threat,
            boolean urgentCalfDefense
    ) {
        if (this.worldObj == null
                || this.worldObj.isRemote
                || threat == null
                || !threat.isEntityAlive()) {
            return;
        }

        long now = this.worldObj.getTotalWorldTime();
        if (now < this.nextWildHerdEventTick
                && (!urgentCalfDefense
                || threat.getEntityId()
                == this.lastWildHerdEventThreatEntityId)) {
            return;
        }
        this.nextWildHerdEventTick =
                now + WILD_HERD_EVENT_COOLDOWN_TICKS;
        this.lastWildHerdEventThreatEntityId =
                threat.getEntityId();

        boolean trackPerformance =
                MumakilPerformanceTracker.isEnabled();
        long scanStart = trackPerformance
                ? MumakilPerformanceTracker.startTimer()
                : 0L;
        List herd = this.worldObj.getEntitiesWithinAABB(
                LOTREntityMumakil.class,
                this.boundingBox.expand(
                        WILD_HERD_EVENT_RADIUS,
                        BABY_TAMING_ADULT_PROTECTION_VERTICAL_RANGE,
                        WILD_HERD_EVENT_RADIUS
                )
        );
        double radiusSq =
                WILD_HERD_EVENT_RADIUS * WILD_HERD_EVENT_RADIUS;
        LOTREntityMumakil lead = this.isWildAdultMumakil()
                ? this
                : findNearestWildAdult(this, herd, radiusSq);
        if (lead == null) {
            return;
        }
        lead.nextWildHerdEventTick = Math.max(
                lead.nextWildHerdEventTick,
                now + WILD_HERD_EVENT_COOLDOWN_TICKS
        );

        int adultRegroupIndex = 0;
        for (int i = 0; i < herd.size(); ++i) {
            LOTREntityMumakil member =
                    (LOTREntityMumakil)herd.get(i);
            if (member.isDead
                    || !member.isEntityAlive()
                    || !member.isWildMumakil()
                    || member.getDistanceSqToEntity(this) > radiusSq) {
                continue;
            }

            if (member.isBabyMumakil()) {
                LOTREntityMumakil nearestAdult =
                        findNearestWildAdult(member, herd, radiusSq);
                if (nearestAdult != null) {
                    member.beginWildHerdRegroupToAdult(
                            nearestAdult,
                            threat,
                            now
                    );
                }
                continue;
            }

            if (urgentCalfDefense) {
                member.clearTerritorialWarning(false);
                member.clearWildHerdRegroup();
                if (member.canTuskAttackTarget(threat)
                        && member.getAttackTarget() != threat) {
                    member.getNavigator().clearPathEntity();
                    member.setAttackTarget(threat);
                }
                continue;
            }

            if (member != lead
                    && member.getAttackTarget() == null
                    && !member.isTerritorialWarningActive()) {
                member.beginWildAdultHerdRegroup(
                        lead,
                        threat,
                        adultRegroupIndex++,
                        now
                );
            }
        }

        if (urgentCalfDefense) {
            lead.playMumakilAngrySound();
        } else {
            lead.faceWildMovePoint(threat.posX, threat.posZ);
        }

        if (trackPerformance) {
            MumakilPerformanceTracker.recordMountTargetScan(
                    this,
                    herd.size(),
                    System.nanoTime() - scanStart
            );
        }
    }

    private static LOTREntityMumakil findNearestWildAdult(
            LOTREntityMumakil member,
            List herd,
            double maximumDistanceSq
    ) {
        LOTREntityMumakil nearest = null;
        double nearestDistanceSq = maximumDistanceSq;
        for (int i = 0; i < herd.size(); ++i) {
            LOTREntityMumakil candidate =
                    (LOTREntityMumakil)herd.get(i);
            if (candidate == member
                    || candidate.isDead
                    || !candidate.isEntityAlive()
                    || !candidate.isWildAdultMumakil()) {
                continue;
            }
            double distanceSq = member.getDistanceSqToEntity(candidate);
            if (distanceSq < nearestDistanceSq) {
                nearestDistanceSq = distanceSq;
                nearest = candidate;
            }
        }
        return nearest;
    }

    private void beginWildAdultHerdRegroup(
            LOTREntityMumakil lead,
            EntityLivingBase threat,
            int regroupIndex,
            long now
    ) {
        int stableIndex = Math.abs(this.getEntityId() * 31
                + regroupIndex * 17);
        double angle = (stableIndex % 360) * Math.PI / 180.0D;
        double radius = WILD_HERD_ADULT_RING_MIN
                + stableIndex % 5
                * (WILD_HERD_ADULT_RING_RANGE / 4.0D);
        double offsetX = Math.cos(angle) * radius;
        double offsetZ = Math.sin(angle) * radius;
        double regroupX = lead.posX + offsetX;
        double regroupZ = lead.posZ + offsetZ;
        double dx = regroupX - this.posX;
        double dz = regroupZ - this.posZ;

        if (this.isWildHerdRegroupActive()) {
            if (this.wildHerdRegroupThreatEntityId
                    == threat.getEntityId()) {
                return;
            }
            this.clearWildHerdRegroup();
        }
        if (dx * dx + dz * dz
                <= WILD_HERD_REGROUP_START_DISTANCE_SQ) {
            return;
        }

        this.wildHerdRegroupX = regroupX;
        this.wildHerdRegroupZ = regroupZ;
        this.wildHerdRegroupOffsetX = offsetX;
        this.wildHerdRegroupOffsetZ = offsetZ;
        this.wildHerdRegroupLeaderEntityId = lead.getEntityId();
        this.wildHerdRegroupThreatEntityId = threat.getEntityId();
        this.wildHerdRegroupUntilTick =
                now + WILD_HERD_REGROUP_DURATION_TICKS;
        this.nextWildHerdRepathTick = this.ticksExisted;
        this.wildHerdRegroupPathOwned = false;
        this.getNavigator().clearPathEntity();
    }

    private void beginWildHerdRegroupToAdult(
            LOTREntityMumakil adult,
            EntityLivingBase threat,
            long now
    ) {
        if (this.isWildHerdRegroupActive()) {
            if (this.wildHerdRegroupThreatEntityId
                    == threat.getEntityId()) {
                return;
            }
            this.clearWildHerdRegroup();
        }

        double dx = adult.posX - this.posX;
        double dz = adult.posZ - this.posZ;
        if (dx * dx + dz * dz
                <= WILD_HERD_REGROUP_START_DISTANCE_SQ) {
            return;
        }

        this.wildHerdRegroupLeaderEntityId = adult.getEntityId();
        this.wildHerdRegroupThreatEntityId = threat.getEntityId();
        this.wildHerdRegroupX = adult.posX;
        this.wildHerdRegroupZ = adult.posZ;
        this.wildHerdRegroupOffsetX = 0.0D;
        this.wildHerdRegroupOffsetZ = 0.0D;
        this.wildHerdRegroupUntilTick =
                now + WILD_HERD_REGROUP_DURATION_TICKS;
        this.nextWildHerdRepathTick = this.ticksExisted;
        this.wildHerdRegroupPathOwned = false;
        this.getNavigator().clearPathEntity();
    }

    private void updateWildHerdRegroup() {
        if (!this.isWildHerdRegroupActive()) {
            if (this.wildHerdRegroupUntilTick != 0L) {
                this.clearWildHerdRegroup();
            }
            return;
        }

        if (!this.isWildMumakil()) {
            this.clearWildHerdRegroup();
            return;
        }
        if (this.getAttackTarget() != null) {
            this.wildHerdRegroupPathOwned = false;
            this.clearWildHerdRegroup();
            return;
        }
        if (this.isTerritorialWarningActive()) {
            this.clearWildHerdRegroup();
            return;
        }

        LOTREntityMumakil leader = null;
        if (this.wildHerdRegroupLeaderEntityId != 0) {
            Entity leaderEntity = this.worldObj.getEntityByID(
                    this.wildHerdRegroupLeaderEntityId
            );
            if (!(leaderEntity instanceof LOTREntityMumakil)
                    || !((LOTREntityMumakil)leaderEntity)
                    .isWildAdultMumakil()) {
                this.clearWildHerdRegroup();
                return;
            }
            leader = (LOTREntityMumakil)leaderEntity;
            this.wildHerdRegroupX = leader.posX
                    + (this.isBabyMumakil()
                    ? 0.0D
                    : this.wildHerdRegroupOffsetX);
            this.wildHerdRegroupZ = leader.posZ
                    + (this.isBabyMumakil()
                    ? 0.0D
                    : this.wildHerdRegroupOffsetZ);
        }

        Entity threat = this.worldObj.getEntityByID(
                this.wildHerdRegroupThreatEntityId
        );
        if (!(threat instanceof EntityLivingBase)
                || !threat.isEntityAlive()) {
            this.clearWildHerdRegroup();
            return;
        }
        if (leader != null
                && !leader.isTerritorialWarningActive()
                && leader.getAttackTarget() == null
                && leader.getDistanceSqToEntity(threat)
                > TERRITORIAL_WARNING_CANCEL_RADIUS
                * TERRITORIAL_WARNING_CANCEL_RADIUS) {
            this.clearWildHerdRegroup();
            return;
        }

        double dx = this.wildHerdRegroupX - this.posX;
        double dz = this.wildHerdRegroupZ - this.posZ;
        double distanceSq = dx * dx + dz * dz;

        if (distanceSq
                <= WILD_HERD_REGROUP_COMPLETE_DISTANCE_SQ) {
            this.clearWildHerdRegroup();
            return;
        }

        if (this.ticksExisted >= this.nextWildHerdRepathTick
                && this.getNavigator().noPath()) {
            this.nextWildHerdRepathTick = this.ticksExisted
                    + WILD_HERD_REPATH_COOLDOWN_TICKS;
            this.wildHerdRegroupPathOwned =
                    this.getNavigator().tryMoveToXYZ(
                            this.wildHerdRegroupX,
                            MathHelper.floor_double(this.boundingBox.minY),
                            this.wildHerdRegroupZ,
                            this.isBabyMumakil()
                                    ? BABY_FOLLOW_SPEED
                                    : WILD_WANDER_SPEED
                    );
        }
    }

    private boolean isWildHerdRegroupActive() {
        return this.worldObj != null
                && this.wildHerdRegroupUntilTick
                > this.worldObj.getTotalWorldTime();
    }

    private void clearWildHerdRegroup() {
        if (this.wildHerdRegroupPathOwned
                && !this.getNavigator().noPath()) {
            this.getNavigator().clearPathEntity();
        }
        this.wildHerdRegroupUntilTick = 0L;
        this.wildHerdRegroupLeaderEntityId = 0;
        this.wildHerdRegroupThreatEntityId = 0;
        this.wildHerdRegroupX = 0.0D;
        this.wildHerdRegroupZ = 0.0D;
        this.wildHerdRegroupOffsetX = 0.0D;
        this.wildHerdRegroupOffsetZ = 0.0D;
        this.nextWildHerdRepathTick = 0;
        this.wildHerdRegroupPathOwned = false;
    }

    private boolean canTargetWildMob(EntityLivingBase target) {
        if (MumakilPerformanceTracker.isEnabled()) {
            MumakilPerformanceTracker.recordMountCandidateCheck(this);
        }

        if (target == this
                || target instanceof EntityPlayer
                || target instanceof LOTREntityMumakil
                || !target.isEntityAlive()
                || target.riddenByEntity != null
                || target.ridingEntity != null) {
            return false;
        }

        if (target instanceof EntityTameable && ((EntityTameable)target).isTamed()) {
            return false;
        }

        if (target instanceof EntityHorse && ((EntityHorse)target).isTame()) {
            return false;
        }

        if (target instanceof EntityAnimal) {
            return false;
        }

        if (target instanceof LOTREntityNPC) {
            LOTREntityNPC npc = (LOTREntityNPC)target;
            return !npc.hiredNPCInfo.isActive;
        }

        return true;
    }

    private int getWildMobTargetPriority(EntityLivingBase target) {
        if (target instanceof IMob) {
            return 0;
        }

        if (target instanceof LOTREntityNPC || !(target instanceof EntityAnimal)) {
            return 1;
        }

        return 2;
    }

    private boolean isPlayerHoldingMelon(EntityPlayer player) {
        if (player == null || !player.isEntityAlive()) {
            return false;
        }

        ItemStack heldItem = player.getCurrentEquippedItem();
        if (heldItem == null) {
            return false;
        }

        return heldItem.getItem() == Items.melon
                || Block.getBlockFromItem(heldItem.getItem()) == Blocks.melon_block;
    }

    /*
     * MUMAKIL_ADULT_BREEDING_AND_FOV_TRANSITION_FIX_V1_1
     *
     * Babies retain their existing lure behavior. Among adults, only
     * player-tamed adults follow melon holders. Wild adults and hired-war
     * Mumakil are explicitly excluded.
     */
    private boolean shouldFollowPlayerHoldingMelon() {
        MumakilMode mode = this.getMumakilMode();
        return mode == MumakilMode.BABY_WILD
                || mode == MumakilMode.BABY_TAMED
                || mode == MumakilMode.ADULT_TAMED;
    }
    public boolean isBabyPanicAnimationActive() {
        return this.babyPanicAnimationActive;
    }

    private void setBabyPanicAnimationActive(boolean active) {
        if (this.babyPanicAnimationActive == active) {
            return;
        }

        this.babyPanicAnimationActive = active;

        if (!this.worldObj.isRemote) {
            this.worldObj.setEntityState(
                    this,
                    active
                            ? MUMAKIL_BABY_PANIC_START_STATUS
                            : MUMAKIL_BABY_PANIC_STOP_STATUS
            );
        }
    }
    private class EntityAIMumakilBabyPanic extends EntityAIPanic {
        private EntityAIMumakilBabyPanic() {
            super(LOTREntityMumakil.this, BABY_PANIC_SPEED);
        }

        @Override
        public boolean shouldExecute() {
            return LOTREntityMumakil.this.isBabyMumakil()
                    && LOTREntityMumakil.this.riddenByEntity == null
                    && super.shouldExecute();
        }

        @Override
        public boolean continueExecuting() {
            return LOTREntityMumakil.this.isBabyMumakil()
                    && LOTREntityMumakil.this.riddenByEntity == null
                    && super.continueExecuting();
        }

        @Override
        public void startExecuting() {
            super.startExecuting();
            LOTREntityMumakil.this.setBabyPanicAnimationActive(true);
        }

        @Override
        public void resetTask() {
            super.resetTask();
            LOTREntityMumakil.this.setBabyPanicAnimationActive(false);
        }
    }

    private class EntityAIMumakilMate extends EntityAIBase {
        private static final int BREEDING_DELAY_TICKS = 60;
        private static final double SEARCH_RANGE = 16.0D;
        private static final double SEARCH_VERTICAL_RANGE = 8.0D;
        private static final double MOVE_SPEED = 1.0D;
        private static final double EXTRA_COMPLETION_PADDING = 2.0D;

        private LOTREntityMumakil targetMate;
        private int breedingDelay;

        private EntityAIMumakilMate() {
            this.setMutexBits(3);
        }

        @Override
        public boolean shouldExecute() {
            if (LOTREntityMumakil.this.getMumakilMode()
                    != MumakilMode.ADULT_TAMED
                    || !LOTREntityMumakil.this.isInLove()
                    || LOTREntityMumakil.this.riddenByEntity != null) {
                return false;
            }

            this.targetMate = this.findNearestMate();
            return this.targetMate != null;
        }

        @Override
        public boolean continueExecuting() {
            return this.targetMate != null
                    && this.targetMate.isEntityAlive()
                    && this.targetMate.isInLove()
                    && this.targetMate.getMumakilMode()
                    == MumakilMode.ADULT_TAMED
                    && this.targetMate.riddenByEntity == null
                    && this.breedingDelay < BREEDING_DELAY_TICKS;
        }

        @Override
        public void resetTask() {
            this.targetMate = null;
            this.breedingDelay = 0;
        }

        @Override
        public void updateTask() {
            LOTREntityMumakil.this.getLookHelper()
                    .setLookPositionWithEntity(
                            this.targetMate,
                            10.0F,
                            (float)LOTREntityMumakil.this
                                    .getVerticalFaceSpeed()
                    );

            double completionDistance =
                    ((double)LOTREntityMumakil.this.width
                            + (double)this.targetMate.width)
                            * 0.5D
                            + EXTRA_COMPLETION_PADDING;
            double completionDistanceSq =
                    completionDistance * completionDistance;

            if (LOTREntityMumakil.this
                    .getDistanceSqToEntity(this.targetMate)
                    > completionDistanceSq) {
                LOTREntityMumakil.this.getNavigator()
                        .tryMoveToEntityLiving(
                                this.targetMate,
                                MOVE_SPEED
                        );
            } else if (!LOTREntityMumakil.this
                    .getNavigator().noPath()) {
                LOTREntityMumakil.this.getNavigator()
                        .clearPathEntity();
            }

            ++this.breedingDelay;

            if (this.breedingDelay >= BREEDING_DELAY_TICKS
                    && LOTREntityMumakil.this
                    .getDistanceSqToEntity(this.targetMate)
                    <= completionDistanceSq) {
                this.spawnMumakCalf();
            }
        }

        private LOTREntityMumakil findNearestMate() {
            List nearby = LOTREntityMumakil.this.worldObj
                    .getEntitiesWithinAABB(
                            LOTREntityMumakil.class,
                            LOTREntityMumakil.this.boundingBox
                                    .expand(
                                            SEARCH_RANGE,
                                            SEARCH_VERTICAL_RANGE,
                                            SEARCH_RANGE
                                    )
                    );

            LOTREntityMumakil nearest = null;
            double nearestDistanceSq = Double.MAX_VALUE;

            for (int i = 0; i < nearby.size(); ++i) {
                LOTREntityMumakil candidate =
                        (LOTREntityMumakil)nearby.get(i);

                if (!LOTREntityMumakil.this
                        .canMateWith(candidate)
                        || candidate.riddenByEntity != null) {
                    continue;
                }

                double distanceSq = LOTREntityMumakil.this
                        .getDistanceSqToEntity(candidate);

                if (distanceSq < nearestDistanceSq) {
                    nearest = candidate;
                    nearestDistanceSq = distanceSq;
                }
            }

            return nearest;
        }

        private void spawnMumakCalf() {
            EntityAgeable childEntity =
                    LOTREntityMumakil.this
                            .createChild(this.targetMate);

            if (!(childEntity instanceof LOTREntityMumakil)) {
                LOTREntityMumakil.this.resetInLove();
                this.targetMate.resetInLove();
                return;
            }

            LOTREntityMumakil child =
                    (LOTREntityMumakil)childEntity;

            EntityPlayer breedingPlayer =
                    LOTREntityMumakil.this.func_146083_cb();

            if (breedingPlayer == null) {
                breedingPlayer = this.targetMate.func_146083_cb();
            }

            if (breedingPlayer != null) {
                breedingPlayer.triggerAchievement(
                        StatList.field_151186_x
                );
            }

            LOTREntityMumakil.this.setGrowingAge(6000);
            this.targetMate.setGrowingAge(6000);
            LOTREntityMumakil.this.resetInLove();
            this.targetMate.resetInLove();

            child.setGrowingAge(-24000);
            child.setLocationAndAngles(
                    (LOTREntityMumakil.this.posX
                            + this.targetMate.posX) * 0.5D,
                    Math.min(
                            LOTREntityMumakil.this.posY,
                            this.targetMate.posY
                    ),
                    (LOTREntityMumakil.this.posZ
                            + this.targetMate.posZ) * 0.5D,
                    LOTREntityMumakil.this.rotationYaw,
                    0.0F
            );

            boolean childSpawned =
                    LOTREntityMumakil.this.worldObj
                            .spawnEntityInWorld(child);
            if (childSpawned && breedingPlayer != null) {
                MumakilAchievements.award(
                        breedingPlayer,
                        MumakilAchievements.breedCalf
                );
            }

            for (int i = 0; i < 7; ++i) {
                double velocityX =
                        LOTREntityMumakil.this.rand.nextGaussian()
                                * 0.02D;
                double velocityY =
                        LOTREntityMumakil.this.rand.nextGaussian()
                                * 0.02D;
                double velocityZ =
                        LOTREntityMumakil.this.rand.nextGaussian()
                                * 0.02D;

                LOTREntityMumakil.this.worldObj.spawnParticle(
                        "heart",
                        child.posX
                                + (double)(
                                LOTREntityMumakil.this.rand
                                        .nextFloat()
                                        * child.width * 2.0F
                        )
                                - (double)child.width,
                        child.posY
                                + 0.5D
                                + (double)(
                                LOTREntityMumakil.this.rand
                                        .nextFloat()
                                        * child.height
                        ),
                        child.posZ
                                + (double)(
                                LOTREntityMumakil.this.rand
                                        .nextFloat()
                                        * child.width * 2.0F
                        )
                                - (double)child.width,
                        velocityX,
                        velocityY,
                        velocityZ
                );
            }

            if (LOTREntityMumakil.this.worldObj
                    .getGameRules()
                    .getGameRuleBooleanValue("doMobLoot")) {
                LOTREntityMumakil.this.worldObj
                        .spawnEntityInWorld(
                                new EntityXPOrb(
                                        LOTREntityMumakil.this.worldObj,
                                        child.posX,
                                        child.posY,
                                        child.posZ,
                                        LOTREntityMumakil.this.rand
                                                .nextInt(7) + 1
                                )
                        );
            }
        }
    }

    private class EntityAIMumakilFollowMelon extends EntityAIBase {
        private EntityPlayer temptingPlayer;
        private int nextSearchTick;
        private int nextRepathTick;

        private EntityAIMumakilFollowMelon() {
            this.setMutexBits(3);
        }

        @Override
        public boolean shouldExecute() {
            if (!LOTREntityMumakil.this.shouldFollowPlayerHoldingMelon()
                    || LOTREntityMumakil.this.riddenByEntity != null
                    || LOTREntityMumakil.this.ticksExisted < this.nextSearchTick) {
                return false;
            }

            this.nextSearchTick = LOTREntityMumakil.this.ticksExisted
                    + BABY_MELON_SEARCH_INTERVAL;

            List nearbyPlayers = LOTREntityMumakil.this.worldObj.getEntitiesWithinAABB(
                    EntityPlayer.class,
                    LOTREntityMumakil.this.boundingBox.expand(
                            BABY_MELON_FOLLOW_RANGE,
                            BABY_FOLLOW_VERTICAL_RANGE,
                            BABY_MELON_FOLLOW_RANGE
                    )
            );

            EntityPlayer nearestPlayer = null;
            double nearestDistanceSq = Double.MAX_VALUE;

            for (int i = 0; i < nearbyPlayers.size(); ++i) {
                EntityPlayer candidate = (EntityPlayer)nearbyPlayers.get(i);
                if (!LOTREntityMumakil.this.isPlayerHoldingMelon(candidate)) {
                    continue;
                }

                double distanceSq = LOTREntityMumakil.this.getDistanceSqToEntity(candidate);
                if (distanceSq < nearestDistanceSq) {
                    nearestPlayer = candidate;
                    nearestDistanceSq = distanceSq;
                }
            }

            if (nearestPlayer == null) {
                return false;
            }

            this.temptingPlayer = nearestPlayer;
            return true;
        }

        @Override
        public boolean continueExecuting() {
            if (!LOTREntityMumakil.this.shouldFollowPlayerHoldingMelon()
                    || LOTREntityMumakil.this.riddenByEntity != null
                    || !LOTREntityMumakil.this.isPlayerHoldingMelon(this.temptingPlayer)) {
                return false;
            }

            return LOTREntityMumakil.this.getDistanceSqToEntity(this.temptingPlayer)
                    <= BABY_MELON_MAX_DISTANCE_SQ;
        }

        @Override
        public void startExecuting() {
            this.nextRepathTick = 0;
        }


        @Override
        public void updateTask() {
            LOTREntityMumakil.this.getLookHelper().setLookPositionWithEntity(
                    this.temptingPlayer,
                    10.0F,
                    (float)LOTREntityMumakil.this.getVerticalFaceSpeed()
            );

            double distanceSq = LOTREntityMumakil.this.getDistanceSqToEntity(
                    this.temptingPlayer
            );

            if (distanceSq <= BABY_MELON_STOP_DISTANCE_SQ) {
                if (!LOTREntityMumakil.this.getNavigator().noPath()) {
                    LOTREntityMumakil.this.getNavigator().clearPathEntity();
                }
                return;
            }

            if (LOTREntityMumakil.this.ticksExisted >= this.nextRepathTick) {
                this.nextRepathTick = LOTREntityMumakil.this.ticksExisted
                        + BABY_MELON_REPATH_INTERVAL;
                LOTREntityMumakil.this.getNavigator().tryMoveToEntityLiving(
                        this.temptingPlayer,
                        BABY_MELON_FOLLOW_SPEED
                );
            }

            PathEntity path = LOTREntityMumakil.this.getNavigator().getPath();
            if (path != null && !path.isFinished()) {
                Vec3 nextPathPosition = path.getPosition(LOTREntityMumakil.this);
                if (nextPathPosition != null) {
                    LOTREntityMumakil.this.faceWildMovePoint(
                            nextPathPosition.xCoord,
                            nextPathPosition.zCoord
                    );
                }
            }
        }

        @Override
        public void resetTask() {
            if (DEBUG_BABY_FAMILY_AI && !LOTREntityMumakil.this.worldObj.isRemote) {
                System.out.println(
                        "[LOTRMoreMobs][BabyFamilyAI] melon-reset"
                                + " baby=" + LOTREntityMumakil.this.getEntityId()
                                + " player=" + (this.temptingPlayer == null
                                ? "none"
                                : this.temptingPlayer.getEntityId())
                                + " tick=" + LOTREntityMumakil.this.ticksExisted
                );
            }

            this.temptingPlayer = null;
            LOTREntityMumakil.this.getNavigator().clearPathEntity();
        }
    }
    // Baby family return: BABY_REMEMBER_ADULT_AFTER_LURE_V1
    private class EntityAIMumakilBabyFollowAdult extends EntityAIBase {
        private LOTREntityMumakil adultMumakil;
        private int nextSearchTick;
        private int nextRepathTick;
        private int nextDiagnosticTick;

        private EntityAIMumakilBabyFollowAdult() {
            this.setMutexBits(1);
        }

        @Override
        public boolean shouldExecute() {
            if (LOTREntityMumakil.this.getMumakilMode() != MumakilMode.BABY_WILD
                    || LOTREntityMumakil.this.riddenByEntity != null
                    || LOTREntityMumakil.this
                    .isWildHerdRegroupActive()) {
                return false;
            }

            if (this.isValidAdultMumakil(this.adultMumakil)) {
                double rememberedDistanceSq = LOTREntityMumakil.this
                        .getDistanceSqToEntity(this.adultMumakil);

                if (rememberedDistanceSq > BABY_FOLLOW_START_DISTANCE_SQ
                        && rememberedDistanceSq
                        <= BABY_FOLLOW_REMEMBERED_MAX_DISTANCE_SQ) {
                    this.debugThrottled(
                            "should=true remembered"
                                    + " adult=" + this.adultMumakil.getEntityId()
                                    + " distance=" + this.formatDistance(rememberedDistanceSq)
                    );
                    return true;
                }

                if (rememberedDistanceSq
                        <= BABY_FOLLOW_REMEMBERED_MAX_DISTANCE_SQ) {
                    this.debugThrottled(
                            "should=false inside-radius"
                                    + " adult=" + this.adultMumakil.getEntityId()
                                    + " distance=" + this.formatDistance(rememberedDistanceSq)
                    );
                    return false;
                }
            }

            this.adultMumakil = null;

            if (LOTREntityMumakil.this.ticksExisted < this.nextSearchTick) {
                return false;
            }

            this.nextSearchTick = LOTREntityMumakil.this.ticksExisted
                    + BABY_FOLLOW_SEARCH_INTERVAL;

            List nearby = LOTREntityMumakil.this.worldObj.getEntitiesWithinAABB(
                    LOTREntityMumakil.class,
                    LOTREntityMumakil.this.boundingBox.expand(
                            BABY_FOLLOW_RANGE,
                            BABY_FOLLOW_VERTICAL_RANGE,
                            BABY_FOLLOW_RANGE
                    )
            );

            LOTREntityMumakil nearestAdult = null;
            double nearestDistanceSq = Double.MAX_VALUE;
            boolean babyTamed = LOTREntityMumakil.this.isTamedMumakilMode();
            int livingAdults = 0;
            int hiredAdults = 0;
            int tameMismatchAdults = 0;
            int compatibleAdults = 0;

            for (int i = 0; i < nearby.size(); ++i) {
                LOTREntityMumakil candidate = (LOTREntityMumakil)nearby.get(i);

                if (candidate == LOTREntityMumakil.this
                        || candidate.isDead
                        || !candidate.isEntityAlive()
                        || !candidate.isAdultMumakil()) {
                    continue;
                }

                ++livingAdults;

                if (candidate.isHiredWarMumakil()) {
                    ++hiredAdults;
                    continue;
                }

                if (candidate.isTamedMumakilMode() != babyTamed) {
                    ++tameMismatchAdults;
                    continue;
                }

                ++compatibleAdults;
                double distanceSq = LOTREntityMumakil.this
                        .getDistanceSqToEntity(candidate);

                if (distanceSq > BABY_FOLLOW_MAX_DISTANCE_SQ
                        || distanceSq >= nearestDistanceSq) {
                    continue;
                }

                nearestAdult = candidate;
                nearestDistanceSq = distanceSq;
            }

            this.adultMumakil = nearestAdult;

            this.debugThrottled(
                    "scan"
                            + " nearby=" + nearby.size()
                            + " livingAdults=" + livingAdults
                            + " hiredRejected=" + hiredAdults
                            + " tameRejected=" + tameMismatchAdults
                            + " compatible=" + compatibleAdults
                            + " selected=" + (nearestAdult == null
                            ? "none"
                            : nearestAdult.getEntityId())
                            + " distance=" + (nearestAdult == null
                            ? "n/a"
                            : this.formatDistance(nearestDistanceSq))
                            + " should=" + (nearestAdult != null
                            && nearestDistanceSq > BABY_FOLLOW_START_DISTANCE_SQ)
            );

            return this.adultMumakil != null
                    && nearestDistanceSq > BABY_FOLLOW_START_DISTANCE_SQ;
        }

        @Override
        public boolean continueExecuting() {
            if (LOTREntityMumakil.this.getMumakilMode() != MumakilMode.BABY_WILD
                    || LOTREntityMumakil.this.riddenByEntity != null
                    || LOTREntityMumakil.this
                    .isWildHerdRegroupActive()
                    || !this.isValidAdultMumakil(this.adultMumakil)
                    || this.adultMumakil.isTamedMumakilMode()
                    != LOTREntityMumakil.this.isTamedMumakilMode()) {
                this.debugEvent(
                        "continue=false invalid"
                                + " adult=" + (this.adultMumakil == null
                                ? "none"
                                : this.adultMumakil.getEntityId())
                );
                return false;
            }

            double distanceSq = LOTREntityMumakil.this.getDistanceSqToEntity(
                    this.adultMumakil
            );
            boolean continueFollowing = distanceSq > BABY_FOLLOW_STOP_DISTANCE_SQ
                    && distanceSq <= BABY_FOLLOW_REMEMBERED_MAX_DISTANCE_SQ;

            if (!continueFollowing) {
                this.debugEvent(
                        "continue=false distance"
                                + " adult=" + this.adultMumakil.getEntityId()
                                + " distance=" + this.formatDistance(distanceSq)
                );
            }

            return continueFollowing;
        }

        @Override
        public void startExecuting() {
            this.nextRepathTick = 0;
            this.debugEvent(
                    "start"
                            + " adult=" + (this.adultMumakil == null
                            ? "none"
                            : this.adultMumakil.getEntityId())
            );
        }

        @Override
        public void updateTask() {
            LOTREntityMumakil.this.getLookHelper().setLookPositionWithEntity(
                    this.adultMumakil,
                    10.0F,
                    (float)LOTREntityMumakil.this.getVerticalFaceSpeed()
            );

            if (LOTREntityMumakil.this.ticksExisted >= this.nextRepathTick) {
                this.nextRepathTick = LOTREntityMumakil.this.ticksExisted
                        + BABY_FOLLOW_REPATH_INTERVAL;

                double distanceSq = LOTREntityMumakil.this.getDistanceSqToEntity(
                        this.adultMumakil
                );
                boolean accepted = LOTREntityMumakil.this.getNavigator()
                        .tryMoveToEntityLiving(
                                this.adultMumakil,
                                BABY_FOLLOW_SPEED
                        );

                this.debugThrottled(
                        "path"
                                + " adult=" + this.adultMumakil.getEntityId()
                                + " distance=" + this.formatDistance(distanceSq)
                                + " accepted=" + accepted
                                + " noPath=" + LOTREntityMumakil.this
                                .getNavigator().noPath()
                );
            }
        }

        @Override
        public void resetTask() {
            this.debugEvent(
                    "reset"
                            + " adult=" + (this.adultMumakil == null
                            ? "none"
                            : this.adultMumakil.getEntityId())
            );

            LOTREntityMumakil.this.getNavigator().clearPathEntity();

            if (LOTREntityMumakil.this.getMumakilMode() != MumakilMode.BABY_WILD
                    || !this.isValidAdultMumakil(this.adultMumakil)) {
                this.adultMumakil = null;
            }
        }

        private boolean isValidAdultMumakil(LOTREntityMumakil candidate) {
            return candidate != null
                    && candidate != LOTREntityMumakil.this
                    && !candidate.isDead
                    && candidate.isEntityAlive()
                    && candidate.getMumakilMode() == MumakilMode.ADULT_WILD
                    && !candidate.getBelongsToNPC();
        }

        private String formatDistance(double distanceSq) {
            return String.format("%.2f", Math.sqrt(Math.max(0.0D, distanceSq)));
        }

        private void debugThrottled(String message) {
            if (!DEBUG_BABY_FAMILY_AI
                    || LOTREntityMumakil.this.worldObj.isRemote
                    || LOTREntityMumakil.this.ticksExisted
                    < this.nextDiagnosticTick) {
                return;
            }

            this.nextDiagnosticTick = LOTREntityMumakil.this.ticksExisted + 20;
            this.debugEvent(message);
        }

        private void debugEvent(String message) {
            if (!DEBUG_BABY_FAMILY_AI
                    || LOTREntityMumakil.this.worldObj.isRemote) {
                return;
            }

            System.out.println(
                    "[LOTRMoreMobs][BabyFamilyAI]"
                            + " baby=" + LOTREntityMumakil.this.getEntityId()
                            + " tick=" + LOTREntityMumakil.this.ticksExisted
                            + " mode=" + LOTREntityMumakil.this.getMumakilMode()
                            + " " + message
            );
        }
    }
    private class EntityAIWildMumakilMove extends EntityAIBase {
        private int nextChasePathTick;
        private int nextWanderPathTick;
        private int fallbackMoveTicks;
        private double fallbackX;
        private double fallbackZ;

        public EntityAIWildMumakilMove() {
            this.setMutexBits(1);
        }

        @Override
        public boolean shouldExecute() {
            return LOTREntityMumakil.this.isWildMumakil()
                    && (LOTREntityMumakil.this.shouldWildChaseTarget()
                    || LOTREntityMumakil.this.shouldWildWander());
        }

        @Override
        public boolean continueExecuting() {
            if (!LOTREntityMumakil.this.isWildMumakil()) {
                return false;
            }

            if (LOTREntityMumakil.this.shouldWildChaseTarget()) {
                return true;
            }

            return LOTREntityMumakil.this.shouldWildWander()
                    && (!LOTREntityMumakil.this.getNavigator().noPath() || this.fallbackMoveTicks > 0);
        }

        @Override
        public void resetTask() {
            this.fallbackMoveTicks = 0;
        }

        @Override
        public void updateTask() {
            if (LOTREntityMumakil.this.shouldWildChaseTarget()) {
                this.updateChaseMovement();
            } else if (LOTREntityMumakil.this.shouldWildWander()) {
                this.updateWanderMovement();
            }
        }

        private void updateChaseMovement() {
            EntityLivingBase target = LOTREntityMumakil.this.getAttackTarget();
            if (target == null) {
                return;
            }

            if (LOTREntityMumakil.this.ticksExisted >= this.nextChasePathTick) {
                /*
                 * AGGRO_TREE_CORRIDOR_AND_WILD_BABY_FOLLOW_V1
                 * Remove leaf/log collision before asking the navigator for
                 * the next chase route.
                 */
                LOTREntityMumakil.this.clearAggroObstaclesForMovement(true);

                boolean trackPerformance =
                        MumakilPerformanceTracker.isEnabled();
                long perfStart = trackPerformance
                        ? MumakilPerformanceTracker.startTimer()
                        : 0L;
                boolean pathAccepted = LOTREntityMumakil.this.getNavigator().tryMoveToEntityLiving(target, WILD_CHASE_SPEED);
                if (trackPerformance) {
                    MumakilPerformanceTracker.recordMountPathRequest(
                            LOTREntityMumakil.this,
                            System.nanoTime() - perfStart,
                            pathAccepted && !LOTREntityMumakil.this.getNavigator().noPath(),
                            true
                    );
                }
                this.nextChasePathTick = LOTREntityMumakil.this.ticksExisted + WILD_CHASE_REPATH_INTERVAL;
            }

            LOTREntityMumakil.this.setAIMoveSpeed((float)WILD_CHASE_SPEED);

            if (LOTREntityMumakil.this.getNavigator().noPath()) {
                this.applyFallbackMove(
                        target.posX,
                        target.posZ,
                        WILD_CHASE_SPEED,
                        WILD_CHASE_FALLBACK_ACCELERATION,
                        WILD_CHASE_FALLBACK_MAX_SPEED,
                        TUSK_ATTACK_RANGE
                );
            }
        }

        private void updateWanderMovement() {
            if (!LOTREntityMumakil.this.getNavigator().noPath()) {
                LOTREntityMumakil.this.setAIMoveSpeed((float)WILD_WANDER_SPEED);
                return;
            }

            if (this.fallbackMoveTicks > 0) {
                if (this.applyFallbackMove(
                        this.fallbackX,
                        this.fallbackZ,
                        WILD_WANDER_SPEED,
                        WILD_WANDER_FALLBACK_ACCELERATION,
                        WILD_WANDER_FALLBACK_MAX_SPEED,
                        WILD_WANDER_FALLBACK_STOP_RANGE
                )) {
                    --this.fallbackMoveTicks;
                } else {
                    this.fallbackMoveTicks = 0;
                }
                return;
            }

            if (LOTREntityMumakil.this.ticksExisted < this.nextWanderPathTick) {
                return;
            }

            this.nextWanderPathTick = LOTREntityMumakil.this.ticksExisted
                    + WILD_WANDER_MIN_INTERVAL
                    + LOTREntityMumakil.this.rand.nextInt(WILD_WANDER_RANDOM_INTERVAL);

            Vec3 wanderTarget = RandomPositionGenerator.findRandomTarget(
                    LOTREntityMumakil.this,
                    WILD_WANDER_RADIUS,
                    WILD_WANDER_VERTICAL_RANGE
            );

            if (wanderTarget == null) {
                return;
            }

            boolean trackPerformance =
                    MumakilPerformanceTracker.isEnabled();
            long perfStart = trackPerformance
                    ? MumakilPerformanceTracker.startTimer()
                    : 0L;
            boolean pathAccepted = LOTREntityMumakil.this.getNavigator().tryMoveToXYZ(
                    wanderTarget.xCoord,
                    wanderTarget.yCoord,
                    wanderTarget.zCoord,
                    WILD_WANDER_SPEED
            );
            boolean pathUsable = pathAccepted && !LOTREntityMumakil.this.getNavigator().noPath();

            if (trackPerformance) {
                MumakilPerformanceTracker.recordMountPathRequest(
                        LOTREntityMumakil.this,
                        System.nanoTime() - perfStart,
                        pathUsable,
                        false
                );
            }

            if (!pathUsable) {
                this.fallbackX = wanderTarget.xCoord;
                this.fallbackZ = wanderTarget.zCoord;
                this.fallbackMoveTicks = WILD_FALLBACK_MOVE_TICKS;
            }
        }

        private boolean applyFallbackMove(
                double x,
                double z,
                double aiSpeed,
                double acceleration,
                double maxSpeed,
                double stopRange
        ) {
            if (!LOTREntityMumakil.this.isWildMumakil()) {
                return false;
            }

            LOTREntityMumakil.this.faceWildMovePoint(x, z);

            double dx = x - LOTREntityMumakil.this.posX;
            double dz = z - LOTREntityMumakil.this.posZ;
            double distSq = dx * dx + dz * dz;
            if (distSq <= stopRange * stopRange || distSq < 1.0E-4D) {
                LOTREntityMumakil.this.moveForward = 0.0F;
                LOTREntityMumakil.this.moveStrafing = 0.0F;
                return false;
            }

            double dist = Math.sqrt(distSq);
            LOTREntityMumakil.this.motionX += dx / dist * acceleration;
            LOTREntityMumakil.this.motionZ += dz / dist * acceleration;

            double horizontalMotionSq = LOTREntityMumakil.this.motionX * LOTREntityMumakil.this.motionX
                    + LOTREntityMumakil.this.motionZ * LOTREntityMumakil.this.motionZ;
            if (horizontalMotionSq > maxSpeed * maxSpeed) {
                double horizontalMotion = Math.sqrt(horizontalMotionSq);
                LOTREntityMumakil.this.motionX = LOTREntityMumakil.this.motionX / horizontalMotion * maxSpeed;
                LOTREntityMumakil.this.motionZ = LOTREntityMumakil.this.motionZ / horizontalMotion * maxSpeed;
            }

            LOTREntityMumakil.this.setAIMoveSpeed((float)aiSpeed);
            LOTREntityMumakil.this.moveForward = 1.0F;
            LOTREntityMumakil.this.moveStrafing = 0.0F;
            LOTREntityMumakil.this.velocityChanged = true;
            return true;
        }
    }


    // ---------------------------------------------------------------------
    // Howdah / saddle / inventory helpers
    // ---------------------------------------------------------------------

    public boolean hasMumakilHowdahEquipped() {
        return !this.isChild()
                && (this.authoritativeMumakilHowdahEquipped
                || this.hasMumakilHowdahInventoryStack()
                || this.getMumakilSyncedArmorIndex() > 0);
    }

    private boolean hasMumakilHowdahInventoryStack() {
        ItemStack stack = this.getMumakilInventoryStack(1);
        return stack != null && stack.getItem() == Main.mumakilHowdah;
    }

    public boolean isMumakilHowdahEquipped() {
        return this.hasMumakilHowdahEquipped();
    }

    public boolean hasMumakilSaddleEquipped() {
        ItemStack stack = this.getMumakilInventoryStack(0);
        return this.isMountSaddled()
                || stack != null && stack.getItem() == Items.saddle;
    }

    private boolean hasMumakilSaddleInventoryStack() {
        ItemStack stack = this.getMumakilInventoryStack(0);
        return stack != null && stack.getItem() == Items.saddle;
    }

    private int getMumakilSyncedArmorIndex() {
        try {
            return this.dataWatcher.getWatchableObjectInt(HORSE_ARMOR_WATCHER_ID);
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Client render state comes from the horse armor watcher. Keeping this
     * accessor separate prevents the renderer from probing the inherited horse
     * inventory every frame while preserving the inventory as server authority.
     */
    public boolean hasMumakilSyncedHowdahEquipped() {
        return !this.isChild()
                && this.getMumakilSyncedArmorIndex() > 0;
    }

    private void setMumakilSyncedArmorIndex(int armorIndex) {
        try {
            this.dataWatcher.updateObject(HORSE_ARMOR_WATCHER_ID, Integer.valueOf(armorIndex));
        } catch (Exception e) {
        }
    }

    private void updateMumakilHowdahSyncState() {
        if (this.worldObj.isRemote) {
            return;
        }

        this.authoritativeMumakilHowdahEquipped =
                !this.isChild() && this.hasMumakilHowdahInventoryStack();
        int desiredArmorIndex = this.authoritativeMumakilHowdahEquipped
                ? MUMAKIL_HOWDAH_SYNC_ARMOR_INDEX
                : 0;

        if (this.getMumakilSyncedArmorIndex() != desiredArmorIndex) {
            this.setMumakilSyncedArmorIndex(desiredArmorIndex);
        }
    }

    public void setMumakilHowdahEquipped(boolean equipped) {
        if (!this.worldObj.isRemote) {
            this.authoritativeMumakilHowdahEquipped =
                    equipped && !this.isChild();
            this.setMumakilSyncedArmorIndex(
                    this.authoritativeMumakilHowdahEquipped
                            ? MUMAKIL_HOWDAH_SYNC_ARMOR_INDEX
                            : 0
            );
        }
    }

    public boolean tickHowdahRosterLoadGrace() {
        if (this.howdahRosterLoadGraceTicks <= 0) {
            return false;
        }

        --this.howdahRosterLoadGraceTicks;
        return true;
    }

    /**
     * Enforces the player-equipment dependency after a completed inventory
     * mutation. Removing the slot first makes repeated container/tick callbacks
     * idempotent; the exact stack (including NBT) is then returned or dropped.
     */
    public boolean enforceTamedAdultHowdahRequiresSaddle(
            EntityPlayer responsiblePlayer
    ) {
        if (this.worldObj == null
                || this.worldObj.isRemote
                || this.getMumakilMode() != MumakilMode.ADULT_TAMED
                || this.hasMumakilSaddleInventoryStack()) {
            return false;
        }

        IInventory inventory = this.getMumakilMountInventory();
        if (inventory == null || inventory.getSizeInventory() <= 1) {
            return false;
        }

        ItemStack howdah = inventory.getStackInSlot(1);
        if (howdah == null || howdah.getItem() != Main.mumakilHowdah) {
            return false;
        }

        inventory.setInventorySlotContents(1, null);
        inventory.markDirty();
        this.setMumakilSyncedArmorIndex(0);

        boolean mayReturnToPlayer =
                responsiblePlayer != null
                        && !responsiblePlayer.isDead
                        && responsiblePlayer.worldObj == this.worldObj;
        if (mayReturnToPlayer
                && responsiblePlayer.inventory
                .addItemStackToInventory(howdah)) {
            responsiblePlayer.inventory.markDirty();
            return true;
        }

        if (howdah.stackSize > 0) {
            this.entityDropItem(howdah, this.height * 0.5F);
        }
        return true;
    }

    public void setMumakilHowdahPreviewEquipped(boolean equipped) {
        if (this.worldObj.isRemote && !this.addedToChunk) {
            this.mumakilHirePreview = equipped;
            this.setMumakilSyncedArmorIndex(
                    equipped ? MUMAKIL_HOWDAH_SYNC_ARMOR_INDEX : 0
            );
        } else {
            /*
             * This marker belongs only to detached client GUI entities.
             * Mounting, spawning, or equipping a real world Mumak can never
             * retain preview rendering state.
             */
            this.mumakilHirePreview = false;
        }
    }

    public boolean isMumakilHirePreview() {
        return this.mumakilHirePreview
                && this.worldObj != null
                && this.worldObj.isRemote
                && !this.addedToChunk;
    }

    private ItemStack getMumakilInventoryStack(int slot) {
        IInventory inventory = this.getMumakilMountInventory();
        if (inventory == null || slot < 0 || slot >= inventory.getSizeInventory()) {
            return null;
        }

        return inventory.getStackInSlot(slot);
    }

    private boolean setMumakilInventoryStack(int slot, ItemStack stack) {
        IInventory inventory = this.getMumakilMountInventory();
        if (inventory == null || slot < 0 || slot >= inventory.getSizeInventory()) {
            return false;
        }

        inventory.setInventorySlotContents(slot, stack);
        inventory.markDirty();

        if (slot == 1 && !this.worldObj.isRemote) {
            this.updateMumakilHowdahSyncState();
        }

        return true;
    }

    public IInventory getMumakilMountInventory() {
        /*
         * EntityHorse constructs this inventory before the Mumak subclass and
         * keeps the same instance for its lifetime. Cache it per entity; a
         * missing/inaccessible field is also remembered so ticks do not retry.
         */
        if (this.cachedMumakilMountInventory != null) {
            return this.cachedMumakilMountInventory;
        }
        if (this.mumakilMountInventoryReadAttempted) {
            return null;
        }

        this.mumakilMountInventoryReadAttempted = true;
        Field inventoryField = resolveMumakilInventoryField();
        if (inventoryField != null) {
            try {
                Object value = inventoryField.get(this);
                if (value instanceof IInventory) {
                    this.cachedMumakilMountInventory = (IInventory)value;
                }
            } catch (Exception ignored) {
            }
        }
        return this.cachedMumakilMountInventory;
    }

    private static Field resolveMumakilInventoryField() {
        if (mumakilInventoryFieldResolved) {
            return mumakilInventoryField;
        }

        synchronized (LOTREntityMumakil.class) {
            if (!mumakilInventoryFieldResolved) {
                mumakilInventoryField =
                        findMumakilInventoryField(
                                LOTREntityMumakil.class
                        );
                mumakilInventoryFieldResolved = true;
            }
        }
        return mumakilInventoryField;
    }

    private static Field findMumakilInventoryField(Class type) {
        for (int i = 0;
             i < MUMAKIL_INVENTORY_FIELD_NAMES.length;
             ++i) {
            Class current = type;
            while (current != null && current != Object.class) {
                try {
                    Field field = current.getDeclaredField(
                            MUMAKIL_INVENTORY_FIELD_NAMES[i]
                    );
                    field.setAccessible(true);
                    return field;
                } catch (NoSuchFieldException e) {
                    current = current.getSuperclass();
                } catch (Exception e) {
                    return null;
                }
            }
        }
        return null;
    }


    // ---------------------------------------------------------------------
    // Rider placement and player interaction
    // ---------------------------------------------------------------------

    public double getMountedYOffset() {
        double scale = this.getMumakilRenderScale();

        if (this.hasMumakilHowdahEquipped()) {
            return RIDER_HOWDAH_Y * scale;
        }

        if (this.hasMumakilSaddleEquipped()) {
            return RIDER_SADDLE_Y * scale;
        }

        return RIDER_WILD_Y * scale;
    }

    @Override
    public void updateRiderPosition() {
        if (this.riddenByEntity != null) {
            if (this.riddenByEntity instanceof EntityLivingBase
                    && !this.isProjectedMountedRiderClear(
                    this.posX,
                    this.posY,
                    this.posZ,
                    this.renderYawOffset
            )
                    && this.isProjectedMountedRiderClear(
                    this.posX,
                    this.posY,
                    this.posZ,
                    this.prevRenderYawOffset
            )) {
                this.renderYawOffset = this.prevRenderYawOffset;
                this.rotationYaw = this.prevRenderYawOffset;
            }
            this.positionRiderAtMumakilAnchor(this.riddenByEntity);
        }
    }

    /**
     * Keep a mounted living rider out of solid blocks when the Mumak moves.
     * The navigation and direct-movement code only knows about the Mumak's
     * body, so use the same seat transform as the real rider update for a
     * side-effect-free projected collision check.
     */
    @Override
    public void moveEntity(double dx, double dy, double dz) {
        if ((dx != 0.0D || dz != 0.0D)
                && this.riddenByEntity instanceof EntityLivingBase
                && !this.isProjectedMountedRiderClear(
                this.posX + dx,
                this.posY + dy,
                this.posZ + dz
        )) {
            this.motionX = 0.0D;
            this.motionZ = 0.0D;
            this.getNavigator().clearPathEntity();
            super.moveEntity(0.0D, dy, 0.0D);
            return;
        }

        super.moveEntity(dx, dy, dz);
    }

    private boolean isProjectedMountedRiderClear(
            double mumakX,
            double mumakY,
            double mumakZ
    ) {
        return this.isProjectedMountedRiderClear(
                mumakX,
                mumakY,
                mumakZ,
                this.renderYawOffset
        );
    }

    private boolean isProjectedMountedRiderClear(
            double mumakX,
            double mumakY,
            double mumakZ,
            float riderYaw
    ) {
        if (!(this.riddenByEntity instanceof EntityLivingBase)
                || this.worldObj == null) {
            return true;
        }

        EntityLivingBase rider = (EntityLivingBase)this.riddenByEntity;
        Vec3 anchor = this.calculateRiderSeatPosition(
                rider,
                riderYaw,
                mumakX,
                mumakY,
                mumakZ
        );
        AxisAlignedBB projectedRiderBox = AxisAlignedBB.getBoundingBox(
                anchor.xCoord - rider.width * 0.5D,
                anchor.yCoord,
                anchor.zCoord - rider.width * 0.5D,
                anchor.xCoord + rider.width * 0.5D,
                anchor.yCoord + rider.height,
                anchor.zCoord + rider.width * 0.5D
        );
        return this.worldObj.func_147461_a(projectedRiderBox).isEmpty();
    }

    /**
     * Shared pure attachment transform for the real driver and client-only
     * hiring-preview driver. This does not mount or tick the supplied entity.
     */
    public void positionRiderAtMumakilAnchor(Entity rider) {
        Vec3 anchor = this.getMumakilRiderAnchor(rider);
        if (anchor == null) {
            return;
        }

        double beforeX = rider.posX;
        double beforeY = rider.posY;
        double beforeZ = rider.posZ;
        rider.setPosition(
                anchor.xCoord,
                anchor.yCoord,
                anchor.zCoord
        );

        if (rider instanceof EntityPlayer) {
            this.recordPlayerSeatDiagnostic(
                    (EntityPlayer)rider,
                    anchor,
                    beforeX,
                    beforeY,
                    beforeZ
            );
        }
    }

    /**
     * Returns the shared server/render world-space rider-feet anchor. Player
     * seats, NPC drivers, and preview riders all use the current visible body
     * yaw. Player view yaw remains independent and is never used as a second
     * rider-position authority.
     */
    public Vec3 getMumakilRiderAnchor(Entity rider) {
        if (rider == null) {
            return null;
        }

        if (rider instanceof EntityPlayer) {
            return this.calculatePlayerSeatPosition(
                    (EntityPlayer)rider
            );
        }

        return this.calculateRiderSeatPosition(
                rider,
                this.renderYawOffset
        );
    }

    /**
     * Calculates a player seat from the Mumak's current absolute position and
     * current body yaw. No player, previous-tick, or cached world position
     * participates in the calculation. The player argument contributes only
     * the normal rider Y offset.
     */
    public Vec3 calculatePlayerSeatPosition(EntityPlayer player) {
        if (player == null) {
            return null;
        }

        return this.calculateRiderSeatPosition(player, this.renderYawOffset);
    }

    private Vec3 calculateRiderSeatPosition(
            Entity rider,
            float placementYaw
    ) {
        return this.calculateRiderSeatPosition(
                rider,
                placementYaw,
                this.posX,
                this.posY,
                this.posZ
        );
    }

    private Vec3 calculateRiderSeatPosition(
            Entity rider,
            float placementYaw,
            double mumakX,
            double mumakY,
            double mumakZ
    ) {

        boolean hasHowdah = this.hasMumakilHowdahEquipped();
        double forwardOffset;
        double sideOffset;

        if (hasHowdah) {
            forwardOffset = RIDER_HOWDAH_FORWARD;
            sideOffset = RIDER_HOWDAH_SIDE;
        } else if (this.hasMumakilSaddleEquipped()) {
            forwardOffset = RIDER_SADDLE_FORWARD;
            sideOffset = RIDER_SADDLE_SIDE;
        } else {
            forwardOffset = RIDER_WILD_FORWARD;
            sideOffset = RIDER_WILD_SIDE;
        }

        double scale = this.getMumakilRenderScale();
        forwardOffset *= scale;
        sideOffset *= scale;

        double verticalOffset = this.getMountedYOffset() + rider.getYOffset();
        float yawRadians = placementYaw * 3.1415927F / 180.0F;

        double forwardX = -MathHelper.sin(yawRadians) * forwardOffset;
        double forwardZ = MathHelper.cos(yawRadians) * forwardOffset;

        double sideX = MathHelper.cos(yawRadians) * sideOffset;
        double sideZ = MathHelper.sin(yawRadians) * sideOffset;

        return Vec3.createVectorHelper(
                mumakX + forwardX + sideX,
                mumakY + verticalOffset,
                mumakZ + forwardZ + sideZ
        );
    }

    public static boolean isPlayerSeatDiagnosticsEnabled() {
        return DEBUG_PLAYER_SEAT_AND_ARROW_ORIGIN;
    }

    private void recordPlayerSeatDiagnostic(
            EntityPlayer player,
            Vec3 anchor,
            double beforeX,
            double beforeY,
            double beforeZ
    ) {
        if (!DEBUG_PLAYER_SEAT_AND_ARROW_ORIGIN
                || this.worldObj == null
                || this.worldObj.isRemote
                || player.ridingEntity != this
                || this.riddenByEntity != player) {
            return;
        }

        long worldTick = this.worldObj.getTotalWorldTime();
        boolean newMountSample =
                this.debugSeatPlayerEntityId != player.getEntityId()
                        || this.debugSeatLastSampleTick == Long.MIN_VALUE
                        || worldTick < this.debugSeatLastSampleTick
                        || worldTick > this.debugSeatLastSampleTick + 1L;
        if (newMountSample) {
            this.debugSeatPlayerEntityId = player.getEntityId();
            this.debugSeatLastMumakX = this.posX;
            this.debugSeatLastMumakZ = this.posZ;
            this.debugSeatTravelDistance = 0.0D;
            this.debugSeatLoggedFirstDistance = false;
            this.debugSeatLoggedSecondDistance = false;
            this.logPlayerSeatDiagnostic(
                    "MOUNTED",
                    player,
                    anchor,
                    beforeX,
                    beforeY,
                    beforeZ
            );
        } else {
            double movementX = this.posX - this.debugSeatLastMumakX;
            double movementZ = this.posZ - this.debugSeatLastMumakZ;
            this.debugSeatTravelDistance += Math.sqrt(
                    movementX * movementX + movementZ * movementZ
            );
            this.debugSeatLastMumakX = this.posX;
            this.debugSeatLastMumakZ = this.posZ;

            if (!this.debugSeatLoggedFirstDistance
                    && this.debugSeatTravelDistance
                    >= DEBUG_PLAYER_SEAT_FIRST_DISTANCE) {
                this.debugSeatLoggedFirstDistance = true;
                this.logPlayerSeatDiagnostic(
                        "MOVED_APPROX_10_BLOCKS",
                        player,
                        anchor,
                        beforeX,
                        beforeY,
                        beforeZ
                );
            }

            if (!this.debugSeatLoggedSecondDistance
                    && this.debugSeatTravelDistance
                    >= DEBUG_PLAYER_SEAT_SECOND_DISTANCE) {
                this.debugSeatLoggedSecondDistance = true;
                this.logPlayerSeatDiagnostic(
                        "MOVED_APPROX_30_BLOCKS",
                        player,
                        anchor,
                        beforeX,
                        beforeY,
                        beforeZ
                );
            }
        }
        this.debugSeatLastSampleTick = worldTick;
    }

    private void logPlayerSeatDiagnostic(
            String reason,
            EntityPlayer player,
            Vec3 anchor,
            double beforeX,
            double beforeY,
            double beforeZ
    ) {
        Entity ridingEntity = player.ridingEntity;
        System.out.println(
                "[LOTRMoreMobs][MumakPlayerSeat]"
                        + " reason=" + reason
                        + " worldTick="
                        + this.worldObj.getTotalWorldTime()
                        + " logicalSide="
                        + (this.worldObj.isRemote ? "CLIENT" : "SERVER")
                        + " playerEntityId=" + player.getEntityId()
                        + " mumakEntityId=" + this.getEntityId()
                        + " playerRidingEntityClass="
                        + (ridingEntity == null
                        ? "null"
                        : ridingEntity.getClass().getName())
                        + " playerRidingEntityId="
                        + (ridingEntity == null
                        ? -1
                        : ridingEntity.getEntityId())
                        + " mumakRiddenByEntityIsPlayer="
                        + (this.riddenByEntity == player)
                        + " mumakPos=" + formatDiagnosticPosition(
                        this.posX,
                        this.posY,
                        this.posZ
                )
                        + " mumakPrevPos=" + formatDiagnosticPosition(
                        this.prevPosX,
                        this.prevPosY,
                        this.prevPosZ
                )
                        + " mumakRotationYaw=" + this.rotationYaw
                        + " mumakRenderYawOffset=" + this.renderYawOffset
                        + " playerViewYaw=" + player.rotationYaw
                        + " playerPosBeforeSeatUpdate="
                        + formatDiagnosticPosition(
                        beforeX,
                        beforeY,
                        beforeZ
                )
                        + " playerMinusSeatBeforeUpdate="
                        + formatDiagnosticPosition(
                        beforeX - anchor.xCoord,
                        beforeY - anchor.yCoord,
                        beforeZ - anchor.zCoord
                )
                        + " playerPos=" + formatDiagnosticPosition(
                        player.posX,
                        player.posY,
                        player.posZ
                )
                        + " playerPrevPos=" + formatDiagnosticPosition(
                        player.prevPosX,
                        player.prevPosY,
                        player.prevPosZ
                )
                        + " playerEyePos=" + formatDiagnosticPosition(
                        player.posX,
                        player.posY + player.getEyeHeight(),
                        player.posZ
                )
                        + " calculatedSeatAnchor="
                        + formatDiagnosticPosition(
                        anchor.xCoord,
                        anchor.yCoord,
                        anchor.zCoord
                )
                        + " playerMinusSeatAnchor="
                        + formatDiagnosticPosition(
                        player.posX - anchor.xCoord,
                        player.posY - anchor.yCoord,
                        player.posZ - anchor.zCoord
                )
                        + " arrowPosBeforeAddonHandling=NA"
                        + " arrowPosAfterAddonHandling=NA"
                        + " arrowInitialPos=NA"
                        + " arrowShootingEntityClass=NA"
                        + " arrowShootingEntityId=-1"
                        + " arrowChanged=false"
                        + " arrowPositionChangeCount=0"
                        + " playerPositionUpdatePath="
                        + "LOTREntityMumakil.updateRiderPosition"
                        + " arrowPositionCorrectionPath=NONE"
        );
    }

    private static String formatDiagnosticPosition(
            double x,
            double y,
            double z
    ) {
        return "(" + x + "," + y + "," + z + ")";
    }

    @Override
    public boolean shouldRiderSit() {
        return true;
    }

    private boolean hasActiveBabyMelonFedWindow() {
        return this.isBabyMumakil()
                && this.worldObj != null
                && this.worldObj.getTotalWorldTime() < this.babyMelonFedUntilTick;
    }

    private boolean tryHealTamedMumakWithMelon(EntityPlayer player) {
        MumakilMode mode = this.getMumakilMode();
        boolean baby = mode == MumakilMode.BABY_TAMED;
        boolean adult = mode == MumakilMode.ADULT_TAMED;
        if ((!baby && !adult)
                || !this.isPlayerHoldingMelon(player)) {
            return false;
        }

        /*
         * Babies always consume this interaction. Adults fall through to the
         * inherited breeding path when no heal is available.
         */
        if (this.worldObj.isRemote) {
            return baby || this.getHealth() < this.getMaxHealth();
        }

        if (!this.isOwner(player)) {
            this.worldObj.setEntityState(this, (byte)6);
            return true;
        }

        long worldTime = this.worldObj.getTotalWorldTime();
        if (this.getHealth() >= this.getMaxHealth()
                || worldTime < this.tamedBabyMelonHealUntilTick) {
            return baby;
        }

        ItemStack heldItem = player.getCurrentEquippedItem();
        if (heldItem == null) {
            return true;
        }

        this.heal(TAMED_BABY_MELON_HEAL_AMOUNT);
        this.tamedBabyMelonHealUntilTick =
                worldTime + TAMED_BABY_MELON_HEAL_COOLDOWN_TICKS;

        if (!player.capabilities.isCreativeMode) {
            --heldItem.stackSize;
            if (heldItem.stackSize <= 0) {
                player.inventory.setInventorySlotContents(
                        player.inventory.currentItem,
                        null
                );
            }
            player.inventory.markDirty();
        }

        player.swingItem();

        this.worldObj.playSoundAtEntity(
                this,
                "random.eat",
                1.0F,
                0.9F + this.rand.nextFloat() * 0.2F
        );
        this.worldObj.setEntityState(this, (byte)7);
        return true;
    }
    private boolean tryFeedWildBabyMelon(EntityPlayer player) {
        if (this.getMumakilMode() != MumakilMode.BABY_WILD
                || !this.isPlayerHoldingMelon(player)) {
            return false;
        }

        /*
         * Return true on both sides so the melon click is treated as feeding,
         * rather than falling through into horse mounting. Inventory, timer,
         * sound, and particles are changed only on the server.
         */
        if (this.worldObj.isRemote) {
            return true;
        }

        ItemStack heldItem = player.getCurrentEquippedItem();
        if (heldItem == null) {
            return false;
        }

        this.babyMelonFedUntilTick = this.worldObj.getTotalWorldTime()
                + BABY_MELON_FED_WINDOW_TICKS;

        if (!player.capabilities.isCreativeMode) {
            --heldItem.stackSize;
            if (heldItem.stackSize <= 0) {
                player.inventory.setInventorySlotContents(
                        player.inventory.currentItem,
                        null
                );
            }
            player.inventory.markDirty();
        }

        player.swingItem();

        this.worldObj.playSoundAtEntity(
                this,
                "random.eat",
                1.0F,
                0.9F + this.rand.nextFloat() * 0.2F
        );

        /*
         * EntityHorse handles status 7 as positive heart feedback. This class
         * passes unrecognized status bytes to super.handleHealthUpdate().
         */
        this.worldObj.setEntityState(this, (byte)7);
        return true;
    }
    // Recently fed wild-baby mounting: BABY_FED_MOUNT_GATE_V1
    private void alertNearbyWildAdultsToBabyTaming(EntityPlayer player) {
        if (this.worldObj.isRemote
                || player == null
                || !player.isEntityAlive()
                || player.capabilities.isCreativeMode) {
            return;
        }

        this.alertNearbyWildAdultsToBabyThreat(player);
    }

    private void alertNearbyWildAdultsToBabyAttack(DamageSource source) {
        if (this.worldObj.isRemote
                || this.getMumakilMode() != MumakilMode.BABY_WILD) {
            return;
        }

        EntityLivingBase attacker =
                this.resolveBabyHerdDefenseAttacker(source);

        if (attacker == null
                || attacker == this
                || !attacker.isEntityAlive()) {
            return;
        }

        this.alertNearbyWildAdultsToBabyThreat(attacker);
    }

    private EntityLivingBase resolveBabyHerdDefenseAttacker(
            DamageSource source
    ) {
        if (source == null) {
            return null;
        }

        /*
         * EntityDamageSourceIndirect normally reports the shooter through
         * getEntity(). Keep an explicit arrow fallback for modded or unusual
         * projectile damage sources that expose only the arrow itself.
         */
        Entity responsibleEntity = source.getEntity();

        if (!(responsibleEntity instanceof EntityLivingBase)) {
            Entity directDamageEntity = source.getSourceOfDamage();

            if (directDamageEntity instanceof EntityArrow) {
                Entity arrowShooter =
                        ((EntityArrow)directDamageEntity).shootingEntity;

                if (arrowShooter instanceof EntityLivingBase) {
                    responsibleEntity = arrowShooter;
                }
            } else if (directDamageEntity instanceof EntityLivingBase) {
                responsibleEntity = directDamageEntity;
            }
        }

        return responsibleEntity instanceof EntityLivingBase
                ? (EntityLivingBase)responsibleEntity
                : null;
    }

    private void alertNearbyWildAdultsToBabyThreat(
            EntityLivingBase attacker
    ) {
        if (this.worldObj.isRemote
                || attacker == null
                || !attacker.isEntityAlive()) {
            return;
        }
        this.triggerWildHerdRegroup(attacker, true);
    }

    // Recently fed wild-baby mounting: BABY_FED_MOUNT_GATE_V1
    private boolean tryMountFedWildBaby(EntityPlayer player) {
        if (player == null
                || this.getMumakilMode() != MumakilMode.BABY_WILD
                || player.isSneaking()
                || player.ridingEntity != null
                || this.riddenByEntity != null
                || player.getCurrentEquippedItem() != null) {
            return false;
        }

        /*
         * Wild babies consume an empty-hand mounting attempt here instead of
         * falling through to EntityHorse, whose normal child interaction does
         * not mount the player.
         */
        if (!this.hasActiveBabyMelonFedWindow()) {
            if (!this.worldObj.isRemote) {
                // Vanilla horse failure feedback; handled by the superclass.
                this.worldObj.setEntityState(this, (byte)6);
            }
            return true;
        }

        if (!this.worldObj.isRemote) {
            this.getNavigator().clearPathEntity();
            player.mountEntity(this);

            /*
             * Alert adults only after mounting actually succeeds. Feeding,
             * melon-luring, failed unfed clicks, and ordinary nearby movement
             * do not run this scan.
             */
            if (player.ridingEntity == this
                    && this.riddenByEntity == player) {
                this.alertNearbyWildAdultsToBabyTaming(player);
            }
        }

        return true;
    }
    private boolean tryMountTamedBaby(EntityPlayer player) {
        if (player == null
                || this.getMumakilMode() != MumakilMode.BABY_TAMED
                || player.isSneaking()
                || player.ridingEntity != null
                || this.riddenByEntity != null
                || player.getCurrentEquippedItem() != null) {
            return false;
        }

        /*
         * A tamed baby keeps its owner. Only that owner may mount it through
         * this custom child-mount path. A saddle is not required merely to sit
         * on the Mumakil; it is still required for normal riding control.
         */
        if (!this.isOwner(player)) {
            if (!this.worldObj.isRemote) {
                this.worldObj.setEntityState(this, (byte)6);
            }
            return true;
        }

        if (!this.worldObj.isRemote) {
            this.getNavigator().clearPathEntity();
            player.mountEntity(this);
        }

        return true;
    }
    private boolean shouldRejectWildBabyHowdahRightClick(
            EntityPlayer player
    ) {
        if (player == null) {
            return false;
        }

        ItemStack held = player.getCurrentEquippedItem();
        return held != null
                && held.getItem() == Main.mumakilHowdah
                && this.getMumakilMode() == MumakilMode.BABY_WILD;
    }

    private boolean tryMountTamedMumakWithEquipment(
            EntityPlayer player
    ) {
        if (player == null
                || player.isSneaking()
                || this.getBelongsToNPC()
                || !this.isTame()) {
            return false;
        }

        ItemStack held = player.getCurrentEquippedItem();
        if (held == null
                || held.getItem() != Items.saddle
                && held.getItem() != Main.mumakilHowdah) {
            return false;
        }

        /*
         * A tamed baby has always been owner-ridable only. Adults retain the
         * inherited LOTR horse mounting policy. In either case the equipment
         * item is never consumed or inserted by this ordinary right-click.
         */
        if (this.getMumakilMode() == MumakilMode.BABY_TAMED
                && !this.isOwner(player)) {
            if (!this.worldObj.isRemote) {
                this.worldObj.setEntityState(this, (byte)6);
            }
            return true;
        }

        if (player.ridingEntity == null
                && this.riddenByEntity == null
                && !this.worldObj.isRemote) {
            this.getNavigator().clearPathEntity();
            player.mountEntity(this);
        }
        return true;
    }

    private boolean handleTamedMumakInventoryInteraction(
            EntityPlayer player
    ) {
        if (player == null || !player.isSneaking() || !this.isTame()) {
            return false;
        }

        if (this.canPlayerUseMumakilInventory(player)) {
            this.openGUI(player);
        } else if (!this.worldObj.isRemote) {
            player.addChatMessage(
                    new ChatComponentTranslation(
                            "chat.lotrmoremobs.mumak.ownerOnly"
                    )
            );
        }
        return true;
    }
    private boolean shouldRejectWildAdultTamingAttempt(EntityPlayer player) {
        return player != null
                && this.getMumakilMode() == MumakilMode.ADULT_WILD
                && !this.isTame()
                && !this.getBelongsToNPC()
                && !player.isSneaking()
                && player.ridingEntity == null
                && this.riddenByEntity == null
                && player.getCurrentEquippedItem() == null;
    }
    public boolean interact(EntityPlayer player) {
        if (this.shouldRouteHiredWarBodyClick()) {
            return this.interactHiredWarDriver(player);
        }

        /*
         * BABY_HOWDAH_RIGHT_CLICK_BLOCK_V1
         * Stop the click before tryEquipMumakilHowdah() or EntityHorse can
         * process it. Do not consume the item.
         */
        if (this.shouldRejectWildBabyHowdahRightClick(player)) {
            if (!this.worldObj.isRemote) {
                this.worldObj.setEntityState(this, (byte)6);
            }
            return true;
        }

        /*
         * ADULT_TAMING_BLOCK_SAFE_DISMOUNT_V1
         * Adult Mumakil cannot enter vanilla horse mounting/taming.
         * Baby taming remains available through the melon-fed baby path.
         */
        if (this.shouldRejectWildAdultTamingAttempt(player)) {
            if (!this.worldObj.isRemote) {
                this.worldObj.setEntityState(this, (byte)6);
            }
            return true;
        }
        if (this.getBelongsToNPC()) {
            return super.interact(player);
        }

        if (this.tryHealTamedMumakWithMelon(player)) {
            return true;
        }

        if (this.tryFeedWildBabyMelon(player)) {
            return true;
        }

        if (this.handleTamedMumakInventoryInteraction(player)) {
            return true;
        }

        if (this.tryMountTamedMumakWithEquipment(player)) {
            return true;
        }

        if (this.tryMountFedWildBaby(player)) {
            return true;
        }

        if (this.tryMountTamedBaby(player)) {
            return true;
        }

        if (player.isSneaking()) {
            this.openGUI(player);
            return true;
        }

        return super.interact(player);
    }

    private boolean shouldRouteHiredWarBodyClick() {
        if (this.isHiredWarMumakil()) {
            return true;
        }

        if (this.riddenByEntity instanceof LOTREntityNPC) {
            LOTREntityNPC driver = (LOTREntityNPC)this.riddenByEntity;
            return driver.hiredNPCInfo != null && driver.hiredNPCInfo.isActive;
        }

        return false;
    }

    private boolean interactHiredWarDriver(EntityPlayer player) {
        LOTREntityNPC driver = this.getLivingHiredWarDriver();

        if (driver == null) {
            return true;
        }

        if (driver.hiredNPCInfo.getHiringPlayer() == player) {
            if (this.worldObj.isRemote) {
                Main.proxy.prepareMumakilHiredDriverGui(driver.getEntityId(), this.getEntityId());
                player.openGui(
                        LOTRMod.instance,
                        LOTRCommonProxy.GUI_ID_HIRED_INTERACT,
                        player.worldObj,
                        driver.getEntityId(),
                        0,
                        0
                );
            } else {
                // Keep client hired data fresh; the initial interact GUI itself is client-only in LOTR.
                driver.hiredNPCInfo.sendClientPacket(false);
            }
        }

        return true;
    }

    private LOTREntityNPC getLivingHiredWarDriver() {
        if (!(this.riddenByEntity instanceof LOTREntityNPC)) {
            return null;
        }

        LOTREntityNPC driver = (LOTREntityNPC)this.riddenByEntity;
        if (driver.isDead || !driver.isEntityAlive() || driver.hiredNPCInfo == null || !driver.hiredNPCInfo.isActive) {
            return null;
        }

        return driver;
    }

    public boolean canPlayerUseMumakilInventory(EntityPlayer player) {
        return player != null
                && !this.getBelongsToNPC()
                && this.isTame()
                && this.isOwner(player);
    }
    public void openGUI(EntityPlayer player) {
        if (!this.canPlayerUseMumakilInventory(player)) {
            return;
        }

        if (!this.worldObj.isRemote) {
            this.updateMumakilHowdahSyncState();
            IInventory inventory = this.getMumakilMountInventory();

            if (inventory != null && player instanceof EntityPlayerMP) {
                EntityPlayerMP playerMP = (EntityPlayerMP)player;

                /*
                 * First let vanilla open the normal horse GUI window on the client.
                 */
                playerMP.displayGUIHorse(this, inventory);

                /*
                 * Then replace the server-side container with a Mumakil-safe version.
                 * This keeps the same GUI but prevents the distance check from instantly closing it.
                 */
                int windowId = playerMP.openContainer.windowId;
                playerMP.openContainer = new ContainerMumakilInventory(playerMP.inventory, inventory, this);
                playerMP.openContainer.windowId = windowId;
                playerMP.openContainer.addCraftingToCrafters(playerMP);

                return;
            }

            super.openGUI(player);
        }
    }


    // ---------------------------------------------------------------------
    // Mount flags, attributes, breeding, and NBT
    // ---------------------------------------------------------------------

    protected boolean isMountHostile() {
        return true;
    }

    protected EntityAIBase createMountAttackAI() {
        return new EntityAIMumakilAttackOnCollide();
    }

    private boolean hasLivingNPCCombatDriver() {
        if (!(this.riddenByEntity instanceof LOTREntityNPC)) {
            return false;
        }

        LOTREntityNPC driver = (LOTREntityNPC)this.riddenByEntity;
        return !driver.isDead && driver.isEntityAlive();
    }

    private boolean shouldUsePersonalAttackAI() {
        if (this.isChild() || this.riddenByEntity instanceof EntityPlayer) {
            return false;
        }

        if (this.isHiredWarMumakil()) {
            if (!this.hasLivingNPCCombatDriver()) {
                return true;
            }

            EntityLivingBase driverTarget = ((LOTREntityNPC)this.riddenByEntity).getAttackTarget();
            return driverTarget != null
                    && driverTarget.isEntityAlive()
                    && this.getAttackTarget() == driverTarget;
        }

        if (this.getMumakilMode() == MumakilMode.ADULT_TAMED) {
            EntityLivingBase target = this.getAttackTarget();
            EntityLivingBase revengeTarget = this.getAITarget();
            return target != null
                    && target == revengeTarget
                    && (!(target instanceof EntityPlayer) || !this.isOwner((EntityPlayer)target));
        }

        return this.isWildMumakil();
    }

    private boolean hasExpectedRiderTargetPathRequest() {
        if (this.isHiredWarMumakil()
                || !this.getBelongsToNPC()
                || !this.hasLivingNPCCombatDriver()) {
            return false;
        }

        EntityLivingBase target = ((LOTREntityNPC)this.riddenByEntity).getAttackTarget();
        return target != null && target.isEntityAlive();
    }

    private boolean hasActiveHiredWarCombatTarget() {
        if (!this.isHiredWarMumakil()) {
            return false;
        }

        EntityLivingBase target;
        if (this.hasLivingNPCCombatDriver()) {
            target = ((LOTREntityNPC)this.riddenByEntity).getAttackTarget();
        } else {
            target = this.getAttackTarget();
        }

        return target != null && target.isEntityAlive();
    }

    private boolean hasActiveLivingSouthronDriver() {
        if (!this.isHiredWarMumakil() || !(this.riddenByEntity instanceof LOTREntityNPC)) {
            return false;
        }

        LOTREntityNPC driver = (LOTREntityNPC)this.riddenByEntity;
        if (driver.isDead
                || !driver.isEntityAlive()
                || driver.hiredNPCInfo == null
                || !driver.hiredNPCInfo.isActive) {
            return false;
        }

        try {
            return LOTRMod.getNPCFaction(driver) == LOTRFaction.NEAR_HARAD;
        } catch (Exception e) {
            return false;
        }
    }

    /* Recurring repaths remain private inside LOTR; only the directly visible initial path is timed here. */
    private class EntityAIMumakilMoveToRiderTarget extends LOTREntityAIHorseMoveToRiderTarget {
        private EntityAIMumakilMoveToRiderTarget() {
            super(LOTREntityMumakil.this);
        }

        @Override
        public boolean shouldExecute() {
            if (LOTREntityMumakil.this.isHiredWarMumakil()) {
                return false;
            }

            boolean measuresPath = LOTREntityMumakil.this.hasExpectedRiderTargetPathRequest();
            boolean trackPerformance =
                    measuresPath
                            && MumakilPerformanceTracker.isEnabled();
            long start = trackPerformance
                    ? MumakilPerformanceTracker.startTimer()
                    : 0L;
            boolean execute = super.shouldExecute();

            if (trackPerformance) {
                MumakilPerformanceTracker.recordRiderTargetPathRequest(
                        LOTREntityMumakil.this,
                        System.nanoTime() - start,
                        execute
                );
            }

            return execute;
        }

        @Override
        public boolean continueExecuting() {
            return !LOTREntityMumakil.this.isHiredWarMumakil() && super.continueExecuting();
        }

        @Override
        public void startExecuting() {
            super.startExecuting();
            MumakilPerformanceTracker.recordRiderTargetAIStart(LOTREntityMumakil.this);
        }

        @Override
        public void updateTask() {
            super.updateTask();
            MumakilPerformanceTracker.recordRiderTargetAIUpdate(LOTREntityMumakil.this);
        }

        @Override
        public void resetTask() {
            super.resetTask();
            MumakilPerformanceTracker.recordRiderTargetAIReset(LOTREntityMumakil.this);
        }
    }

    private enum MumakilAutonomousCombatState {
        APPROACH,
        ATTACK_PASS,
        TRAMPLE_PASS,
        TURNAROUND
    }

    private class EntityAIMumakilAttackOnCollide extends LOTREntityAIAttackOnCollide {
        private int controlledTargetId = -1;
        private boolean newTargetPathPending;
        private boolean noProgressPathPending;
        private int nextControlledPathTick;
        private int failureBackoffTicks = COMBAT_PATH_FAILURE_BACKOFF_MIN;
        private double lastPathTargetX;
        private double lastPathTargetY;
        private double lastPathTargetZ;
        private double lastProgressX;
        private double lastProgressZ;
        private int nextProgressCheckTick;
        private int noProgressTicks;
        private MumakilAutonomousCombatState autonomousCombatState =
                MumakilAutonomousCombatState.APPROACH;
        private int observedSuccessfulTuskSequence;
        private int nextCombatWaypointPathTick;
        private boolean combatWaypointPathAttempted;
        private int failedCombatWaypointSearches;
        private double combatWaypointX;
        private double combatWaypointY;
        private double combatWaypointZ;
        private double combatWaypointTargetX;
        private double combatWaypointTargetZ;
        private double passReferenceX;
        private double passReferenceZ;
        private double passDirectionX;
        private double passDirectionZ;
        private boolean hasCombatWaypoint;
        private int turnaroundDirectionSign = 1;

        private EntityAIMumakilAttackOnCollide() {
            super(LOTREntityMumakil.this, WILD_ATTACK_SPEED, true);
        }

        @Override
        public boolean shouldExecute() {
            if (this.isHiredWarMountAttackAIDisabled()
                    || !LOTREntityMumakil.this.shouldUsePersonalAttackAI()) {
                return false;
            }

            if (LOTREntityMumakil.this.isHiredWarMumakil()) {
                boolean trackPerformance =
                        MumakilPerformanceTracker.isEnabled();
                long start = trackPerformance
                        ? MumakilPerformanceTracker.startTimer()
                        : 0L;
                EntityLivingBase target = LOTREntityMumakil.this.getAttackTarget();
                boolean execute = target != null && target.isEntityAlive();

                if (execute) {
                    this.attackTarget = target;
                    LOTREntityMumakil.this.getNavigator().setAvoidsWater(
                            LOTREntityMumakil.this.isWarCombatFormation()
                    );
                    this.resetControlledPathForNewTarget(target);
                    if (this.canUseAutonomousCombatPass(target)) {
                        this.updateAutonomousCombatState(
                                target,
                                true
                        );
                    }
                    if (this.autonomousCombatState
                            == MumakilAutonomousCombatState.APPROACH) {
                        this.updateControlledPath(target, true);
                    }
                }

                if (trackPerformance) {
                    MumakilPerformanceTracker.recordMountAttackShould(
                            LOTREntityMumakil.this,
                            System.nanoTime() - start
                    );
                }
                return execute;
            }

            boolean measuresPath = LOTREntityMumakil.this.getAttackTarget() != null;
            boolean trackPerformance =
                    MumakilPerformanceTracker.isEnabled();
            long start = trackPerformance
                    ? MumakilPerformanceTracker.startTimer()
                    : 0L;
            boolean execute = super.shouldExecute();

            if (trackPerformance) {
                long elapsed = System.nanoTime() - start;
                MumakilPerformanceTracker.recordMountAttackShould(LOTREntityMumakil.this, elapsed);
                if (measuresPath) {
                    MumakilPerformanceTracker.recordMountAttackPathRequest(
                            LOTREntityMumakil.this,
                            elapsed,
                            execute
                    );
                }
            }

            return execute;
        }

        @Override
        public boolean continueExecuting() {
            if (this.isHiredWarMountAttackAIDisabled()
                    || !LOTREntityMumakil.this.shouldUsePersonalAttackAI()) {
                return false;
            }

            boolean trackPerformance =
                    MumakilPerformanceTracker.isEnabled();
            long start = trackPerformance
                    ? MumakilPerformanceTracker.startTimer()
                    : 0L;
            boolean execute = super.continueExecuting();
            if (!execute
                    && this.isInitialCombatWaypointPending()) {
                /* Keep a newly-entered pass alive through its existing
                 * entity-ID stagger so the first waypoint search can run. */
                execute = true;
            }
            if (execute && LOTREntityMumakil.this.isHiredWarMumakil()) {
                this.resetControlledPathForNewTarget(this.attackTarget);
            }
            if (trackPerformance) {
                MumakilPerformanceTracker.recordMountAttackContinue(
                        LOTREntityMumakil.this,
                        System.nanoTime() - start
                );
            }
            return execute;
        }

        private boolean isInitialCombatWaypointPending() {
            if (this.autonomousCombatState
                    != MumakilAutonomousCombatState.ATTACK_PASS
                    && this.autonomousCombatState
                    != MumakilAutonomousCombatState.TRAMPLE_PASS) {
                return false;
            }
            if (this.combatWaypointPathAttempted
                    || !LOTREntityMumakil.this.getNavigator().noPath()
                    || this.attackTarget == null
                    || !this.attackTarget.isEntityAlive()
                    || !this.canUseAutonomousCombatPass(this.attackTarget)) {
                return false;
            }
            return LOTREntityMumakil.this.ticksExisted
                    <= this.nextCombatWaypointPathTick;
        }

        @Override
        public void startExecuting() {
            boolean trackPerformance =
                    MumakilPerformanceTracker.isEnabled();
            long start = trackPerformance
                    ? MumakilPerformanceTracker.startTimer()
                    : 0L;
            if (LOTREntityMumakil.this.isHiredWarMumakil()) {
                if (this.entityPathEntity != null) {
                    LOTREntityMumakil.this.getNavigator().setPath(this.entityPathEntity, this.moveSpeed);
                    this.entityPathEntity = null;
                }
                this.pathCheckTimer = 0;
            } else {
                super.startExecuting();
            }
            if (trackPerformance) {
                MumakilPerformanceTracker.recordMountAttackStart(
                        LOTREntityMumakil.this,
                        System.nanoTime() - start
                );
                MumakilPerformanceTracker.recordMountAttackAIStart(
                        LOTREntityMumakil.this
                );
            }
        }

        @Override
        public void updateTask() {
            boolean trackPerformance =
                    MumakilPerformanceTracker.isEnabled();
            long start = trackPerformance
                    ? MumakilPerformanceTracker.startTimer()
                    : 0L;
            super.updateTask();
            if (this.canUseAutonomousCombatPass(this.attackTarget)) {
                /*
                 * A successful tusk hit can happen inside super.updateTask().
                 * Move immediately into the drive-through phase without
                 * surrendering the navigator to a second AI task.
                 */
                this.observeSuccessfulTuskHit(
                        this.attackTarget,
                        false
                );
            }
            if (trackPerformance) {
                MumakilPerformanceTracker.recordMountAttackUpdate(
                        LOTREntityMumakil.this,
                        System.nanoTime() - start
                );
            }
        }

        @Override
        public void resetTask() {
            boolean trackPerformance =
                    MumakilPerformanceTracker.isEnabled();
            long start = trackPerformance
                    ? MumakilPerformanceTracker.startTimer()
                    : 0L;
            this.clearAutonomousCombatState("task-reset");
            super.resetTask();
            if (trackPerformance) {
                MumakilPerformanceTracker.recordMountAttackReset(
                        LOTREntityMumakil.this,
                        System.nanoTime() - start
                );
                MumakilPerformanceTracker.recordMountAttackAIReset(
                        LOTREntityMumakil.this
                );
            }
        }

        @Override
        protected void updateLookAndPathing() {
            if (!LOTREntityMumakil.this.isHiredWarMumakil()) {
                super.updateLookAndPathing();
                return;
            }

            this.resetControlledPathForNewTarget(this.attackTarget);
            if (this.canUseAutonomousCombatPass(this.attackTarget)) {
                this.updateAutonomousCombatState(
                        this.attackTarget,
                        false
                );
                if (this.autonomousCombatState
                        != MumakilAutonomousCombatState.APPROACH) {
                    this.updateCombatWaypointNavigation(
                            this.attackTarget,
                            false
                    );
                }
                if (this.hasCombatWaypoint) {
                    LOTREntityMumakil.this.getLookHelper().setLookPosition(
                            this.combatWaypointX,
                            this.combatWaypointY
                                    + LOTREntityMumakil.this.getEyeHeight(),
                            this.combatWaypointZ,
                            30.0F,
                            30.0F
                    );
                } else {
                    LOTREntityMumakil.this.getLookHelper()
                            .setLookPositionWithEntity(
                                    this.attackTarget,
                                    30.0F,
                                    30.0F
                            );
                }
                if (this.autonomousCombatState
                        != MumakilAutonomousCombatState.APPROACH) {
                    return;
                }
            } else {
                this.clearAutonomousCombatState("not-eligible");
            }

            LOTREntityMumakil.this.getLookHelper().setLookPositionWithEntity(this.attackTarget, 30.0F, 30.0F);
            this.updateControlledPath(this.attackTarget, false);
        }

        private void resetControlledPathForNewTarget(EntityLivingBase target) {
            int targetId = target.getEntityId();
            if (targetId == this.controlledTargetId) {
                return;
            }

            this.controlledTargetId = targetId;
            this.newTargetPathPending = true;
            this.noProgressPathPending = false;
            this.nextControlledPathTick = LOTREntityMumakil.this.ticksExisted;
            this.failureBackoffTicks = COMBAT_PATH_FAILURE_BACKOFF_MIN;
            this.lastPathTargetX = target.posX;
            this.lastPathTargetY = target.posY;
            this.lastPathTargetZ = target.posZ;
            this.lastProgressX = LOTREntityMumakil.this.posX;
            this.lastProgressZ = LOTREntityMumakil.this.posZ;
            this.nextProgressCheckTick = LOTREntityMumakil.this.ticksExisted + COMBAT_PATH_PROGRESS_CHECK_TICKS;
            this.noProgressTicks = 0;
            this.entityPathEntity = null;
            this.clearAutonomousCombatState("new-target");
            this.observedSuccessfulTuskSequence =
                    LOTREntityMumakil.this.successfulTuskAttackSequence;
            LOTREntityMumakil.this.getNavigator().clearPathEntity();
        }

        private void updateAutonomousCombatState(
                EntityLivingBase target,
                boolean preparingStart
        ) {
            if (!this.canUseAutonomousCombatPass(target)) {
                this.clearAutonomousCombatState("not-eligible");
                return;
            }

            if (this.observeSuccessfulTuskHit(
                    target,
                    preparingStart
            )) {
                return;
            }

            double targetDistanceSq =
                    LOTREntityMumakil.this.getDistanceSqToEntity(
                            target
                    );
            boolean closeEnoughForPass =
                    targetDistanceSq
                            <= AUTONOMOUS_ATTACK_PASS_TRIGGER_DISTANCE
                            * AUTONOMOUS_ATTACK_PASS_TRIGGER_DISTANCE;

            if (this.autonomousCombatState
                    == MumakilAutonomousCombatState.APPROACH) {
                if (!closeEnoughForPass) {
                    return;
                }
                if (LOTREntityMumakil.this
                        .tuskAttackCooldownTicks <= 0) {
                    this.beginAttackPass(
                            target,
                            preparingStart,
                            "attack-ready"
                    );
                } else {
                    this.beginTramplePass(
                            target,
                            preparingStart,
                            "cooldown-active"
                    );
                }
                return;
            }

            if (this.autonomousCombatState
                    == MumakilAutonomousCombatState.TRAMPLE_PASS
                    && LOTREntityMumakil.this
                    .tuskAttackCooldownTicks <= 0) {
                if (closeEnoughForPass
                        || targetDistanceSq
                        <= AUTONOMOUS_TURNAROUND_MAX_TARGET_DISTANCE
                        * AUTONOMOUS_TURNAROUND_MAX_TARGET_DISTANCE) {
                    this.beginAttackPass(
                            target,
                            preparingStart,
                            "cooldown-ready"
                    );
                } else {
                    this.enterApproach("cooldown-ready-target-far");
                }
            }
        }

        private boolean observeSuccessfulTuskHit(
                EntityLivingBase target,
                boolean preparingStart
        ) {
            int successfulSequence =
                    LOTREntityMumakil.this.successfulTuskAttackSequence;
            if (successfulSequence
                    == this.observedSuccessfulTuskSequence) {
                return false;
            }

            this.observedSuccessfulTuskSequence =
                    successfulSequence;
            this.beginTramplePass(
                    target,
                    preparingStart,
                    "successful-tusk-hit"
            );
            return true;
        }

        private boolean canUseAutonomousCombatPass(
                EntityLivingBase target
        ) {
            if (target == null
                    || !target.isEntityAlive()
                    || LOTREntityMumakil.this.riddenByEntity
                    instanceof EntityPlayer
                    || !LOTREntityMumakil.this
                    .isWarCombatFormation()
                    || !LOTREntityMumakil.this
                    .hasMumakilHowdahEquipped()) {
                return false;
            }

            if (LOTREntityMumakil.this.hasLivingNPCCombatDriver()) {
                LOTREntityNPC driver =
                        (LOTREntityNPC)LOTREntityMumakil.this
                                .riddenByEntity;
                return driver.getAttackTarget() == target
                        && LOTREntityMumakil.this.getAttackTarget()
                        == target;
            }
            return LOTREntityMumakil.this.getAttackTarget() == target;
        }

        private void beginAttackPass(
                EntityLivingBase target,
                boolean preparingStart,
                String reason
        ) {
            this.enterWaypointState(
                    MumakilAutonomousCombatState.ATTACK_PASS,
                    target,
                    reason
            );
            this.updateCombatWaypointNavigation(
                    target,
                    preparingStart
            );
        }

        private void beginTramplePass(
                EntityLivingBase target,
                boolean preparingStart,
                String reason
        ) {
            this.enterWaypointState(
                    MumakilAutonomousCombatState.TRAMPLE_PASS,
                    target,
                    reason
            );
            this.updateCombatWaypointNavigation(
                    target,
                    preparingStart
            );
        }

        private void beginTurnaround(
                EntityLivingBase target,
                boolean preparingStart,
                String reason
        ) {
            this.turnaroundDirectionSign =
                    -this.turnaroundDirectionSign;
            this.enterWaypointState(
                    MumakilAutonomousCombatState.TURNAROUND,
                    target,
                    reason
            );
            this.updateCombatWaypointNavigation(
                    target,
                    preparingStart
            );
        }

        private void enterWaypointState(
                MumakilAutonomousCombatState state,
                EntityLivingBase target,
                String reason
        ) {
            MumakilAutonomousCombatState previous =
                    this.autonomousCombatState;
            this.autonomousCombatState = state;
            LOTREntityMumakil.this
                    .mumakilAutonomousCombatPassActive = true;
            this.failedCombatWaypointSearches = 0;
            this.hasCombatWaypoint = false;
            this.combatWaypointPathAttempted = false;
            this.combatWaypointTargetX = target.posX;
            this.combatWaypointTargetZ = target.posZ;
            this.nextCombatWaypointPathTick =
                    LOTREntityMumakil.this.ticksExisted
                            + this.getWaypointInitialStagger();
            this.entityPathEntity = null;
            if (!LOTREntityMumakil.this.getNavigator().noPath()) {
                LOTREntityMumakil.this.getNavigator()
                        .clearPathEntity();
            }
            if (previous != state
                    && state
                    == MumakilAutonomousCombatState.TRAMPLE_PASS) {
                MumakilServerPerformanceDiagnostics
                        .recordTramplePassTransition(
                                LOTREntityMumakil.this.worldObj
                        );
            }
            this.debugAutonomousCombat(
                    "transition=" + state
                            + " reason=" + reason
                            + " navigatorOwner=attack-on-collide"
            );
        }

        private void enterApproach(String reason) {
            MumakilAutonomousCombatState previous =
                    this.autonomousCombatState;
            this.autonomousCombatState =
                    MumakilAutonomousCombatState.APPROACH;
            LOTREntityMumakil.this
                    .mumakilAutonomousCombatPassActive = false;
            this.failedCombatWaypointSearches = 0;
            this.hasCombatWaypoint = false;
            this.newTargetPathPending = true;
            this.noProgressPathPending = false;
            this.nextControlledPathTick =
                    LOTREntityMumakil.this.ticksExisted;
            this.failureBackoffTicks =
                    COMBAT_PATH_FAILURE_BACKOFF_MIN;
            this.entityPathEntity = null;
            if (!LOTREntityMumakil.this.getNavigator().noPath()) {
                LOTREntityMumakil.this.getNavigator()
                        .clearPathEntity();
            }
            if (previous
                    != MumakilAutonomousCombatState.APPROACH) {
                this.debugAutonomousCombat(
                        "transition=APPROACH reason=" + reason
                );
            }
        }

        private void clearAutonomousCombatState(String reason) {
            MumakilAutonomousCombatState previous =
                    this.autonomousCombatState;
            this.autonomousCombatState =
                    MumakilAutonomousCombatState.APPROACH;
            LOTREntityMumakil.this
                    .mumakilAutonomousCombatPassActive = false;
            this.failedCombatWaypointSearches = 0;
            this.hasCombatWaypoint = false;
            if (previous
                    != MumakilAutonomousCombatState.APPROACH) {
                this.debugAutonomousCombat(
                        "transition=APPROACH reason=" + reason
                );
            }
        }

        private void updateCombatWaypointNavigation(
                EntityLivingBase target,
                boolean preparingStart
        ) {
            if (this.autonomousCombatState
                    == MumakilAutonomousCombatState.APPROACH) {
                return;
            }

            if (this.hasCompletedCombatWaypoint()) {
                this.handleCombatWaypointComplete(
                        target,
                        preparingStart
                );
                return;
            }

            double targetMoveX =
                    target.posX - this.combatWaypointTargetX;
            double targetMoveZ =
                    target.posZ - this.combatWaypointTargetZ;
            boolean targetMoved =
                    targetMoveX * targetMoveX
                            + targetMoveZ * targetMoveZ
                            >= AUTONOMOUS_TARGET_WAYPOINT_REPATH_DISTANCE_SQ;
            boolean needsPath =
                    !this.hasCombatWaypoint
                            || targetMoved
                            || LOTREntityMumakil.this.getNavigator().noPath();

            int currentTick = LOTREntityMumakil.this.ticksExisted;
            if (!needsPath
                    || currentTick
                    < this.nextCombatWaypointPathTick) {
                return;
            }

            this.combatWaypointPathAttempted = true;

            long destinationSearchStart =
                    MumakilServerPerformanceDiagnostics
                            .startTimer(
                                    LOTREntityMumakil.this.worldObj
                            );
            boolean turnaround =
                    this.autonomousCombatState
                            == MumakilAutonomousCombatState.TURNAROUND;
            PathEntity combatPath = turnaround
                    ? this.findValidatedTurnaroundPath(target)
                    : this.findValidatedPassThroughPath(target);
            long destinationSearchNanos =
                    System.nanoTime() - destinationSearchStart;
            if (turnaround) {
                MumakilServerPerformanceDiagnostics
                        .recordTurnaroundSearch(
                                LOTREntityMumakil.this.worldObj,
                                destinationSearchNanos
                        );
            } else {
                MumakilServerPerformanceDiagnostics
                        .recordPassThroughSearch(
                                LOTREntityMumakil.this.worldObj,
                                destinationSearchNanos
                        );
            }

            this.nextCombatWaypointPathTick =
                    currentTick
                            + (turnaround
                            ? AUTONOMOUS_TURN_REPATH_INTERVAL
                            : AUTONOMOUS_PASS_REPATH_INTERVAL);
            this.combatWaypointTargetX = target.posX;
            this.combatWaypointTargetZ = target.posZ;

            long combatPathStart =
                    MumakilServerPerformanceDiagnostics
                            .startTimer(
                                    LOTREntityMumakil.this.worldObj
                            );
            boolean combatPathAccepted = combatPath != null;
            if (combatPathAccepted) {
                if (preparingStart) {
                    this.entityPathEntity = combatPath;
                } else {
                    combatPathAccepted =
                            LOTREntityMumakil.this
                                    .getNavigator()
                                    .setPath(
                                            combatPath,
                                            this.moveSpeed
                                    );
                }
            }
            long combatPathNanos =
                    System.nanoTime() - combatPathStart;
            if (turnaround) {
                MumakilServerPerformanceDiagnostics
                        .recordTurnaroundPath(
                                LOTREntityMumakil.this.worldObj,
                                combatPathNanos,
                                combatPathAccepted
                        );
            } else {
                MumakilServerPerformanceDiagnostics
                        .recordPassThroughPath(
                                LOTREntityMumakil.this.worldObj,
                                combatPathNanos,
                                combatPathAccepted
                        );
            }

            if (combatPathAccepted) {
                this.failedCombatWaypointSearches = 0;
                this.hasCombatWaypoint = true;
                this.debugAutonomousCombat(
                        "waypoint-path=accepted"
                                + " destination="
                                + this.combatWaypointX
                                + ","
                                + this.combatWaypointY
                                + ","
                                + this.combatWaypointZ
                                + " navigatorOwner=attack-on-collide"
                );
                return;
            }

            ++this.failedCombatWaypointSearches;
            this.hasCombatWaypoint = false;
            MumakilServerPerformanceDiagnostics
                    .recordPassWaypointFailure(
                            LOTREntityMumakil.this.worldObj
                    );
            this.debugAutonomousCombat(
                    "waypoint-path=rejected"
                            + " failures="
                            + this.failedCombatWaypointSearches
            );
            if (this.failedCombatWaypointSearches
                    >= AUTONOMOUS_WAYPOINT_MAX_FAILED_SEARCHES) {
                if (turnaround) {
                    MumakilDriverControlEventHandler
                            .rejectAutonomousTargetAfterCombatPathFailure(
                                    LOTREntityMumakil.this,
                                    target
                            );
                    this.enterApproach(
                            "turnaround-waypoint-failures"
                    );
                } else {
                    this.beginTurnaround(
                            target,
                            preparingStart,
                            "pass-waypoint-failures"
                    );
                }
            }
        }

        private boolean hasCompletedCombatWaypoint() {
            if (!this.hasCombatWaypoint) {
                return false;
            }

            double waypointDeltaX =
                    this.combatWaypointX
                            - LOTREntityMumakil.this.posX;
            double waypointDeltaZ =
                    this.combatWaypointZ
                            - LOTREntityMumakil.this.posZ;
            if (waypointDeltaX * waypointDeltaX
                    + waypointDeltaZ * waypointDeltaZ
                    <= AUTONOMOUS_WAYPOINT_REACHED_DISTANCE
                    * AUTONOMOUS_WAYPOINT_REACHED_DISTANCE) {
                return true;
            }

            if (this.autonomousCombatState
                    == MumakilAutonomousCombatState.ATTACK_PASS
                    || this.autonomousCombatState
                    == MumakilAutonomousCombatState.TRAMPLE_PASS) {
                double passedX =
                        LOTREntityMumakil.this.posX
                                - this.passReferenceX;
                double passedZ =
                        LOTREntityMumakil.this.posZ
                                - this.passReferenceZ;
                double passProgress =
                        passedX * this.passDirectionX
                                + passedZ * this.passDirectionZ;
                return passProgress
                        >= AUTONOMOUS_PASS_THROUGH_DISTANCE - 2.0D;
            }

            return false;
        }

        private void handleCombatWaypointComplete(
                EntityLivingBase target,
                boolean preparingStart
        ) {
            MumakilAutonomousCombatState completedState =
                    this.autonomousCombatState;
            if (completedState
                    == MumakilAutonomousCombatState.TURNAROUND) {
                if (LOTREntityMumakil.this
                        .tuskAttackCooldownTicks <= 0) {
                    this.beginAttackPass(
                            target,
                            preparingStart,
                            "turn-complete-attack-ready"
                    );
                } else {
                    this.beginTramplePass(
                            target,
                            preparingStart,
                            "turn-complete-cooldown-active"
                    );
                }
                return;
            }

            this.beginTurnaround(
                    target,
                    preparingStart,
                    completedState
                            == MumakilAutonomousCombatState.ATTACK_PASS
                            ? "attack-pass-complete"
                            : "trample-pass-complete"
            );
        }

        private PathEntity findValidatedPassThroughPath(
                EntityLivingBase target
        ) {
            double directionX =
                    target.posX - LOTREntityMumakil.this.posX;
            double directionZ =
                    target.posZ - LOTREntityMumakil.this.posZ;
            double directionLength = Math.sqrt(
                    directionX * directionX
                            + directionZ * directionZ
            );
            if (directionLength < 0.001D) {
                double yawRadians = Math.toRadians(
                        LOTREntityMumakil.this.renderYawOffset
                );
                directionX = -Math.sin(yawRadians);
                directionZ = Math.cos(yawRadians);
            } else {
                directionX /= directionLength;
                directionZ /= directionLength;
            }

            List nearbyEnemies =
                    this.collectBoundedPassEnemies(target);
            double[] candidateScores =
                    new double[AUTONOMOUS_PASS_ANGLES.length];
            boolean[] attempted =
                    new boolean[AUTONOMOUS_PASS_ANGLES.length];
            for (int i = 0;
                 i < AUTONOMOUS_PASS_ANGLES.length;
                 ++i) {
                double angle = Math.toRadians(
                        AUTONOMOUS_PASS_ANGLES[i]
                );
                double cos = Math.cos(angle);
                double sin = Math.sin(angle);
                double candidateDirectionX =
                        directionX * cos - directionZ * sin;
                double candidateDirectionZ =
                        directionX * sin + directionZ * cos;
                double destinationX =
                        target.posX
                                + candidateDirectionX
                                * AUTONOMOUS_PASS_THROUGH_DISTANCE;
                double destinationZ =
                        target.posZ
                                + candidateDirectionZ
                                * AUTONOMOUS_PASS_THROUGH_DISTANCE;
                candidateScores[i] =
                        this.scorePassCorridor(
                                nearbyEnemies,
                                destinationX,
                                destinationZ
                        )
                                - Math.abs(
                                AUTONOMOUS_PASS_ANGLES[i]
                        ) * 0.0025D;
            }

            for (int attempt = 0;
                 attempt < AUTONOMOUS_PASS_ANGLES.length;
                 ++attempt) {
                int bestIndex = -1;
                double bestScore = -Double.MAX_VALUE;
                for (int i = 0;
                     i < candidateScores.length;
                     ++i) {
                    if (!attempted[i]
                            && candidateScores[i] > bestScore) {
                        bestIndex = i;
                        bestScore = candidateScores[i];
                    }
                }
                if (bestIndex < 0) {
                    break;
                }
                attempted[bestIndex] = true;

                double angle = Math.toRadians(
                        AUTONOMOUS_PASS_ANGLES[bestIndex]
                );
                double cos = Math.cos(angle);
                double sin = Math.sin(angle);
                double candidateDirectionX =
                        directionX * cos - directionZ * sin;
                double candidateDirectionZ =
                        directionX * sin + directionZ * cos;
                double destinationX =
                        target.posX
                                + candidateDirectionX
                                * AUTONOMOUS_PASS_THROUGH_DISTANCE;
                double destinationZ =
                        target.posZ
                                + candidateDirectionZ
                                * AUTONOMOUS_PASS_THROUGH_DISTANCE;
                PathEntity path = this.tryCreateCombatWaypointPath(
                        target,
                        destinationX,
                        destinationZ,
                        AUTONOMOUS_PASS_MIN_TARGET_DISTANCE,
                        AUTONOMOUS_PASS_MAX_TARGET_DISTANCE
                );
                if (path != null) {
                    double finalPassProjection =
                            (this.combatWaypointX - target.posX)
                                    * candidateDirectionX
                                    + (this.combatWaypointZ
                                    - target.posZ)
                                    * candidateDirectionZ;
                    if (finalPassProjection
                            >= AUTONOMOUS_PASS_MIN_TARGET_DISTANCE
                            && finalPassProjection
                            <= AUTONOMOUS_PASS_MAX_TARGET_DISTANCE) {
                        this.passReferenceX = target.posX;
                        this.passReferenceZ = target.posZ;
                        this.passDirectionX = candidateDirectionX;
                        this.passDirectionZ = candidateDirectionZ;
                        return path;
                    }
                }
            }

            return null;
        }

        private PathEntity findValidatedTurnaroundPath(
                EntityLivingBase target
        ) {
            double outwardX =
                    LOTREntityMumakil.this.posX - target.posX;
            double outwardZ =
                    LOTREntityMumakil.this.posZ - target.posZ;
            double outwardLength = Math.sqrt(
                    outwardX * outwardX
                            + outwardZ * outwardZ
            );
            if (outwardLength < 0.001D) {
                outwardX = -this.passDirectionX;
                outwardZ = -this.passDirectionZ;
                outwardLength = Math.sqrt(
                        outwardX * outwardX
                                + outwardZ * outwardZ
                );
            }
            if (outwardLength < 0.001D) {
                double yawRadians = Math.toRadians(
                        LOTREntityMumakil.this.renderYawOffset
                );
                outwardX = -Math.sin(yawRadians);
                outwardZ = Math.cos(yawRadians);
            } else {
                outwardX /= outwardLength;
                outwardZ /= outwardLength;
            }

            for (int i = 0;
                 i < AUTONOMOUS_TURNAROUND_ANGLES.length;
                 ++i) {
                double angle = Math.toRadians(
                        AUTONOMOUS_TURNAROUND_ANGLES[i]
                                * this.turnaroundDirectionSign
                );
                double cos = Math.cos(angle);
                double sin = Math.sin(angle);
                double directionX =
                        outwardX * cos - outwardZ * sin;
                double directionZ =
                        outwardX * sin + outwardZ * cos;
                double destinationX =
                        target.posX
                                + directionX
                                * AUTONOMOUS_TURNAROUND_DISTANCE;
                double destinationZ =
                        target.posZ
                                + directionZ
                                * AUTONOMOUS_TURNAROUND_DISTANCE;
                PathEntity path = this.tryCreateCombatWaypointPath(
                        target,
                        destinationX,
                        destinationZ,
                        AUTONOMOUS_TURNAROUND_MIN_TARGET_DISTANCE,
                        AUTONOMOUS_TURNAROUND_MAX_TARGET_DISTANCE
                );
                if (path != null) {
                    return path;
                }
            }

            return null;
        }

        private PathEntity tryCreateCombatWaypointPath(
                EntityLivingBase target,
                double destinationX,
                double destinationZ,
                double minimumTargetDistance,
                double maximumTargetDistance
        ) {
            double destinationY = this.findCombatGroundY(
                    destinationX,
                    destinationZ
            );
            if (Double.isNaN(destinationY)) {
                return null;
            }

            PathEntity path =
                    LOTREntityMumakil.this.getNavigator()
                            .getPathToXYZ(
                                    destinationX,
                                    destinationY,
                                    destinationZ
                            );
            PathPoint finalPoint =
                    path == null
                            ? null
                            : path.getFinalPathPoint();
            if (finalPoint == null) {
                return null;
            }

            double finalX = finalPoint.xCoord + 0.5D;
            double finalZ = finalPoint.zCoord + 0.5D;
            double destinationDeltaX = finalX - destinationX;
            double destinationDeltaZ = finalZ - destinationZ;
            if (destinationDeltaX * destinationDeltaX
                    + destinationDeltaZ * destinationDeltaZ
                    > 36.0D) {
                return null;
            }

            double finalTargetX = finalX - target.posX;
            double finalTargetZ = finalZ - target.posZ;
            double finalTargetDistanceSq =
                    finalTargetX * finalTargetX
                            + finalTargetZ * finalTargetZ;
            if (finalTargetDistanceSq
                    < minimumTargetDistance
                    * minimumTargetDistance
                    || finalTargetDistanceSq
                    > maximumTargetDistance
                    * maximumTargetDistance) {
                return null;
            }

            this.combatWaypointX = finalX;
            this.combatWaypointY = finalPoint.yCoord;
            this.combatWaypointZ = finalZ;
            return path;
        }

        private List collectBoundedPassEnemies(
                EntityLivingBase primaryTarget
        ) {
            ArrayList enemies = new ArrayList();
            enemies.add(primaryTarget);

            AxisAlignedBB scanBox = primaryTarget.boundingBox.expand(
                    AUTONOMOUS_CLUSTER_SCAN_RANGE,
                    AUTONOMOUS_CLUSTER_SCAN_VERTICAL_RANGE,
                    AUTONOMOUS_CLUSTER_SCAN_RANGE
            );
            List nearby = LOTREntityMumakil.this.worldObj
                    .getEntitiesWithinAABB(
                            EntityLivingBase.class,
                            scanBox
                    );
            for (int i = 0;
                 i < nearby.size()
                         && enemies.size()
                         < AUTONOMOUS_CLUSTER_CANDIDATE_LIMIT;
                 ++i) {
                EntityLivingBase candidate =
                        (EntityLivingBase)nearby.get(i);
                if (candidate != primaryTarget
                        && LOTREntityMumakil.this
                        .canTuskAttackTarget(candidate)) {
                    enemies.add(candidate);
                }
            }
            return enemies;
        }

        private double scorePassCorridor(
                List enemies,
                double destinationX,
                double destinationZ
        ) {
            double startX = LOTREntityMumakil.this.posX;
            double startZ = LOTREntityMumakil.this.posZ;
            double lineX = destinationX - startX;
            double lineZ = destinationZ - startZ;
            double lineLengthSq = lineX * lineX + lineZ * lineZ;
            if (lineLengthSq < 0.001D) {
                return 0.0D;
            }

            double corridorRadiusSq =
                    AUTONOMOUS_TRAMPLE_CORRIDOR_RADIUS
                            * AUTONOMOUS_TRAMPLE_CORRIDOR_RADIUS;
            double score = 0.0D;
            for (int i = 0; i < enemies.size(); ++i) {
                EntityLivingBase enemy =
                        (EntityLivingBase)enemies.get(i);
                double enemyX = enemy.posX - startX;
                double enemyZ = enemy.posZ - startZ;
                double projection =
                        (enemyX * lineX + enemyZ * lineZ)
                                / lineLengthSq;
                if (projection < 0.0D || projection > 1.0D) {
                    continue;
                }

                double nearestX = startX + lineX * projection;
                double nearestZ = startZ + lineZ * projection;
                double deltaX = enemy.posX - nearestX;
                double deltaZ = enemy.posZ - nearestZ;
                double corridorDistanceSq =
                        deltaX * deltaX + deltaZ * deltaZ;
                if (corridorDistanceSq <= corridorRadiusSq) {
                    score += 1.0D
                            + (corridorRadiusSq
                            - corridorDistanceSq)
                            / corridorRadiusSq;
                }
            }
            return score;
        }

        private int getWaypointInitialStagger() {
            int entityId = LOTREntityMumakil.this.getEntityId();
            return (entityId & Integer.MAX_VALUE)
                    % AUTONOMOUS_WAYPOINT_STAGGER_TICKS;
        }

        private void debugAutonomousCombat(String message) {
            if (!DEBUG_AUTONOMOUS_COMBAT_AI
                    || LOTREntityMumakil.this.worldObj == null
                    || LOTREntityMumakil.this.worldObj.isRemote) {
                return;
            }
            System.out.println(
                    "[LOTRMoreMobs][MumakAutonomousCombat]"
                            + " mumak="
                            + LOTREntityMumakil.this.getEntityId()
                            + " tick="
                            + LOTREntityMumakil.this.ticksExisted
                            + " state="
                            + this.autonomousCombatState
                            + " origin="
                            + LOTREntityMumakil.this
                            .getFormationOrigin()
                            + " cooldown="
                            + LOTREntityMumakil.this
                            .tuskAttackCooldownTicks
                            + " target="
                            + (this.attackTarget == null
                            ? -1
                            : this.attackTarget.getEntityId())
                            + " targetDistance="
                            + (this.attackTarget == null
                            ? -1.0D
                            : Math.sqrt(
                            LOTREntityMumakil.this
                                    .getDistanceSqToEntity(
                                            this.attackTarget
                                    )
                    ))
                            + " driverTarget="
                            + this.getDebugDriverTargetId()
                            + " hasPath="
                            + !LOTREntityMumakil.this
                            .getNavigator().noPath()
                            + " aiSpeed="
                            + LOTREntityMumakil.this
                            .getAIMoveSpeed()
                            + " moveForward="
                            + LOTREntityMumakil.this.moveForward
                            + " horizontalMotion="
                            + Math.sqrt(
                            LOTREntityMumakil.this.motionX
                                    * LOTREntityMumakil.this
                                    .motionX
                                    + LOTREntityMumakil.this
                                    .motionZ
                                    * LOTREntityMumakil.this
                                    .motionZ
                    )
                            + " "
                            + message
            );
        }

        private int getDebugDriverTargetId() {
            if (!(LOTREntityMumakil.this.riddenByEntity
                    instanceof LOTREntityNPC)) {
                return -1;
            }
            EntityLivingBase target =
                    ((LOTREntityNPC)LOTREntityMumakil.this
                            .riddenByEntity).getAttackTarget();
            return target == null ? -1 : target.getEntityId();
        }

        private double findCombatGroundY(
                double destinationX,
                double destinationZ
        ) {
            int blockX = MathHelper.floor_double(destinationX);
            int blockZ = MathHelper.floor_double(destinationZ);
            int halfWidth =
                    MathHelper.ceiling_float_int(
                            LOTREntityMumakil.this.width * 0.5F
                    );

            if (!this.isCombatChunkAreaLoaded(
                    blockX,
                    blockZ,
                    halfWidth
            )) {
                return Double.NaN;
            }

            int baseY = MathHelper.floor_double(
                    LOTREntityMumakil.this.posY
            );
            for (int offset = 3; offset >= -3; --offset) {
                int groundY = baseY + offset;
                if (groundY <= 0
                        || groundY
                        + MathHelper.ceiling_float_int(
                        LOTREntityMumakil.this.height
                )
                        >= 256
                        || !LOTREntityMumakil.this.worldObj
                        .blockExists(blockX, groundY, blockZ)
                        || !LOTREntityMumakil.this.worldObj
                        .getBlock(blockX, groundY - 1, blockZ)
                        .getMaterial().blocksMovement()
                        || LOTREntityMumakil.this.worldObj
                        .getBlock(blockX, groundY, blockZ)
                        .getMaterial().isLiquid()) {
                    continue;
                }

                AxisAlignedBB destinationBox =
                        AxisAlignedBB.getBoundingBox(
                                destinationX
                                        - LOTREntityMumakil.this.width
                                        * 0.5D,
                                groundY,
                                destinationZ
                                        - LOTREntityMumakil.this.width
                                        * 0.5D,
                                destinationX
                                        + LOTREntityMumakil.this.width
                                        * 0.5D,
                                groundY
                                        + LOTREntityMumakil.this.height,
                                destinationZ
                                        + LOTREntityMumakil.this.width
                                        * 0.5D
                        );
                if (!LOTREntityMumakil.this.worldObj
                        .getCollidingBoundingBoxes(
                                LOTREntityMumakil.this,
                                destinationBox
                        ).isEmpty()
                        || LOTREntityMumakil.this.worldObj
                        .isAnyLiquid(destinationBox)) {
                    continue;
                }
                return groundY;
            }

            return Double.NaN;
        }

        private boolean isCombatChunkAreaLoaded(
                int blockX,
                int blockZ,
                int radius
        ) {
            int minChunkX = blockX - radius >> 4;
            int maxChunkX = blockX + radius >> 4;
            int minChunkZ = blockZ - radius >> 4;
            int maxChunkZ = blockZ + radius >> 4;

            for (int chunkX = minChunkX;
                 chunkX <= maxChunkX;
                 ++chunkX) {
                for (int chunkZ = minChunkZ;
                     chunkZ <= maxChunkZ;
                     ++chunkZ) {
                    if (!LOTREntityMumakil.this.worldObj
                            .getChunkProvider()
                            .chunkExists(chunkX, chunkZ)) {
                        return false;
                    }
                }
            }

            return true;
        }

        private void updateControlledPath(EntityLivingBase target, boolean preparingStart) {
            double targetDistanceSq = LOTREntityMumakil.this.getDistanceSqToEntity(target);

            if (targetDistanceSq <= TUSK_ATTACK_RANGE * TUSK_ATTACK_RANGE) {
                this.resetProgressTracking();
                return;
            }

            this.updateProgressTracking();

            int reason = this.getControlledPathReason(target);
            if (reason == 0) {
                MumakilPerformanceTracker.recordCombatPathSkippedExistingPath(LOTREntityMumakil.this);
                return;
            }

            int currentTick = LOTREntityMumakil.this.ticksExisted;
            if (currentTick < this.nextControlledPathTick || !this.isStaggerTick(currentTick)) {
                MumakilPerformanceTracker.recordCombatPathSkippedCooldown(LOTREntityMumakil.this);
                return;
            }

            boolean trackPerformance =
                    MumakilPerformanceTracker.isEnabled();
            long autonomousPursuitStart =
                    LOTREntityMumakil.this
                            .isAutonomousWarFormation()
                            ? MumakilServerPerformanceDiagnostics
                            .startTimer(
                                    LOTREntityMumakil.this.worldObj
                            )
                            : 0L;
            long pathSearchStart = trackPerformance
                    ? MumakilPerformanceTracker.startTimer()
                    : 0L;
            PathEntity path = null;

            if (reason == MumakilPerformanceTracker.COMBAT_PATH_REASON_NEW_TARGET
                    || reason == MumakilPerformanceTracker.COMBAT_PATH_REASON_TARGET_MOVED) {
                path = this.createDirectCombatPath(target, targetDistanceSq);
            }

            if (path == null) {
                path = LOTREntityMumakil.this.getNavigator().getPathToEntityLiving(target);
            }

            long pathSearchNanos = trackPerformance
                    ? System.nanoTime() - pathSearchStart
                    : 0L;
            boolean accepted = path != null;
            long pathInstallNanos = 0L;

            if (accepted) {
                if (preparingStart) {
                    this.entityPathEntity = path;
                } else {
                    long pathInstallStart = trackPerformance
                            ? MumakilPerformanceTracker.startTimer()
                            : 0L;
                    accepted = LOTREntityMumakil.this.getNavigator().setPath(path, this.moveSpeed);
                    if (trackPerformance) {
                        pathInstallNanos =
                                System.nanoTime() - pathInstallStart;
                    }
                }
            }

            if (LOTREntityMumakil.this
                    .isAutonomousWarFormation()) {
                MumakilServerPerformanceDiagnostics
                        .recordAutonomousPursuitPath(
                                LOTREntityMumakil.this.worldObj,
                                System.nanoTime()
                                        - autonomousPursuitStart,
                                accepted
                        );
            }

            if (trackPerformance) {
                long elapsed = pathSearchNanos + pathInstallNanos;
                MumakilPerformanceTracker.recordCombatPathRequest(
                        LOTREntityMumakil.this,
                        elapsed,
                        accepted,
                        reason
                );
                MumakilPerformanceTracker.recordCombatPathDetail(
                        LOTREntityMumakil.this,
                        pathSearchNanos,
                        pathInstallNanos,
                        accepted,
                        reason,
                        targetDistanceSq,
                        preparingStart
                );
            }

            this.lastPathTargetX = target.posX;
            this.lastPathTargetY = target.posY;
            this.lastPathTargetZ = target.posZ;
            this.newTargetPathPending = false;
            this.noProgressPathPending = false;
            this.noProgressTicks = 0;
            this.lastProgressX = LOTREntityMumakil.this.posX;
            this.lastProgressZ = LOTREntityMumakil.this.posZ;
            this.nextProgressCheckTick = currentTick + COMBAT_PATH_PROGRESS_CHECK_TICKS;

            if (accepted) {
                this.failureBackoffTicks = COMBAT_PATH_FAILURE_BACKOFF_MIN;
                this.nextControlledPathTick = currentTick
                        + (reason == MumakilPerformanceTracker.COMBAT_PATH_REASON_NO_PATH
                        ? COMBAT_PATH_NO_PATH_RETRY_COOLDOWN
                        : COMBAT_PATH_REPATH_COOLDOWN);
            } else {
                MumakilPerformanceTracker.recordCombatPathBackoff(LOTREntityMumakil.this);
                this.nextControlledPathTick = currentTick + this.failureBackoffTicks;
                this.failureBackoffTicks = Math.min(
                        COMBAT_PATH_FAILURE_BACKOFF_MAX,
                        this.failureBackoffTicks * 2
                );
            }
        }

        private PathEntity createDirectCombatPath(EntityLivingBase target, double targetDistanceSq) {
            if (target == null
                    || targetDistanceSq > COMBAT_DIRECT_PATH_MAX_RANGE * COMBAT_DIRECT_PATH_MAX_RANGE
                    || Math.abs(target.posY - LOTREntityMumakil.this.posY)
                    > COMBAT_DIRECT_PATH_MAX_Y_DIFFERENCE
                    || !LOTREntityMumakil.this.canEntityBeSeen(target)) {
                return null;
            }

            int pathX = MathHelper.floor_double(target.posX);
            int pathY = MathHelper.floor_double(target.boundingBox.minY);
            int pathZ = MathHelper.floor_double(target.posZ);

            return new PathEntity(new PathPoint[]{
                    new PathPoint(pathX, pathY, pathZ)
            });
        }

        private int getControlledPathReason(EntityLivingBase target) {
            if (this.newTargetPathPending) {
                return MumakilPerformanceTracker.COMBAT_PATH_REASON_NEW_TARGET;
            }
            if (this.noProgressPathPending) {
                return MumakilPerformanceTracker.COMBAT_PATH_REASON_NO_PROGRESS;
            }

            double movedX = target.posX - this.lastPathTargetX;
            double movedY = target.posY - this.lastPathTargetY;
            double movedZ = target.posZ - this.lastPathTargetZ;
            if (movedX * movedX + movedY * movedY + movedZ * movedZ
                    >= COMBAT_PATH_TARGET_MOVE_THRESHOLD_SQ) {
                return MumakilPerformanceTracker.COMBAT_PATH_REASON_TARGET_MOVED;
            }
            if (LOTREntityMumakil.this.getNavigator().noPath()) {
                return MumakilPerformanceTracker.COMBAT_PATH_REASON_NO_PATH;
            }
            return 0;
        }

        private void updateProgressTracking() {
            int currentTick = LOTREntityMumakil.this.ticksExisted;
            if (currentTick < this.nextProgressCheckTick) {
                return;
            }

            double movedX = LOTREntityMumakil.this.posX - this.lastProgressX;
            double movedZ = LOTREntityMumakil.this.posZ - this.lastProgressZ;
            if (movedX * movedX + movedZ * movedZ >= COMBAT_PATH_PROGRESS_THRESHOLD_SQ) {
                this.noProgressTicks = 0;
            } else {
                this.noProgressTicks += COMBAT_PATH_PROGRESS_CHECK_TICKS;
                if (this.noProgressTicks >= COMBAT_PATH_NO_PROGRESS_TICKS) {
                    this.noProgressPathPending = true;
                }
            }

            this.lastProgressX = LOTREntityMumakil.this.posX;
            this.lastProgressZ = LOTREntityMumakil.this.posZ;
            this.nextProgressCheckTick = currentTick + COMBAT_PATH_PROGRESS_CHECK_TICKS;
        }

        private void resetProgressTracking() {
            this.noProgressTicks = 0;
            this.noProgressPathPending = false;
            this.lastProgressX = LOTREntityMumakil.this.posX;
            this.lastProgressZ = LOTREntityMumakil.this.posZ;
            this.nextProgressCheckTick = LOTREntityMumakil.this.ticksExisted + COMBAT_PATH_PROGRESS_CHECK_TICKS;
        }

        private boolean isStaggerTick(int currentTick) {
            int entitySlot = (LOTREntityMumakil.this.getEntityId() & Integer.MAX_VALUE)
                    % COMBAT_PATH_STAGGER_TICKS;
            return currentTick % COMBAT_PATH_STAGGER_TICKS == entitySlot;
        }

        private boolean isHiredWarMountAttackAIDisabled() {
            return MumakilPerformanceTracker.DEBUG_DISABLE_HIRED_WAR_MOUNT_ATTACK_AI
                    && LOTREntityMumakil.this.isHiredWarMumakil();
        }
    }

    private class EntityAIMumakilFollowHiringPlayer extends LOTREntityAIHorseFollowHiringPlayer {
        private int followUpdatesSinceStart;

        private EntityAIMumakilFollowHiringPlayer() {
            super(LOTREntityMumakil.this);
        }

        @Override
        public boolean shouldExecute() {
            boolean execute = !MumakilDriverControlEventHandler
                    .hasActiveAuthoritativeHiredWarCombatTarget(
                            LOTREntityMumakil.this
                    )
                    && super.shouldExecute();
            MumakilPerformanceTracker.recordMountFollowShould(
                    LOTREntityMumakil.this,
                    false,
                    execute
            );
            return execute;
        }

        @Override
        public boolean continueExecuting() {
            return !MumakilDriverControlEventHandler
                    .hasActiveAuthoritativeHiredWarCombatTarget(
                            LOTREntityMumakil.this
                    )
                    && super.continueExecuting();
        }

        @Override
        public void startExecuting() {
            this.followUpdatesSinceStart = 0;
            super.startExecuting();
            MumakilPerformanceTracker.recordMountFollowStart(LOTREntityMumakil.this);
        }

        @Override
        public void updateTask() {
            boolean pathCall = this.followUpdatesSinceStart % 10 == 0;
            super.updateTask();
            ++this.followUpdatesSinceStart;
            MumakilPerformanceTracker.recordMountFollowUpdate(
                    LOTREntityMumakil.this,
                    pathCall
            );
        }

        @Override
        public void resetTask() {
            super.resetTask();
            this.followUpdatesSinceStart = 0;
        }
    }

    private class EntityAIMumakilHurtByTarget extends EntityAIHurtByTarget {
        private EntityAIMumakilHurtByTarget() {
            super(LOTREntityMumakil.this, false);
        }

        @Override
        public boolean shouldExecute() {
            if (LOTREntityMumakil.this.isBabyMumakil()
                    || LOTREntityMumakil.this.isHiredWarMumakil()
                    && LOTREntityMumakil.this.hasLivingNPCCombatDriver()) {
                return false;
            }

            EntityLivingBase attacker = LOTREntityMumakil.this.getAITarget();
            if (LOTREntityMumakil.this.getMumakilMode() == MumakilMode.ADULT_TAMED
                    && attacker instanceof EntityPlayer
                    && LOTREntityMumakil.this.isOwner((EntityPlayer)attacker)) {
                return false;
            }

            return super.shouldExecute();
        }
    }

    private class EntityAIBlockHiredWarWander extends EntityAIBase {
        private EntityAIBlockHiredWarWander() {
            this.setMutexBits(1);
        }

        @Override
        public boolean shouldExecute() {
            return LOTREntityMumakil.this.hasActiveHiredWarCombatTarget();
        }

        @Override
        public boolean continueExecuting() {
            return LOTREntityMumakil.this.hasActiveHiredWarCombatTarget();
        }
    }

    @Override
    public int getMaxSpawnedInChunk() {
        return 7;
    }

    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        this.applyConfiguredAttributes();
    }

    private void applyConfiguredAttributes() {
        boolean baby = this.shouldUseBabyLifecycleState();

        this.getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(
                baby ? BABY_MAX_HEALTH : MAX_HEALTH
        );

        this.applyConfiguredMovementSpeed();

        this.getEntityAttribute(SharedMonsterAttributes.followRange).setBaseValue(
                baby ? BABY_PATH_SEARCH_RANGE : ADULT_PATH_SEARCH_RANGE
        );

        this.getEntityAttribute(SharedMonsterAttributes.knockbackResistance)
                .setBaseValue(KNOCKBACK_RESISTANCE);

        this.getEntityAttribute(SharedMonsterAttributes.attackDamage)
                .setBaseValue(ATTACK_DAMAGE);
    }

    private void applyConfiguredMovementSpeed() {
        if (this.getEntityAttribute(SharedMonsterAttributes.movementSpeed)
                == null) {
            return;
        }

        double intendedBaseSpeed = this.shouldUseBabyLifecycleState()
                ? BABY_MOVEMENT_SPEED
                : MOVEMENT_SPEED;
        this.getEntityAttribute(SharedMonsterAttributes.movementSpeed)
                .setBaseValue(
                        intendedBaseSpeed
                                * (double)this.getMumakilSpeedTrait()
                );
    }

    protected void onLOTRHorseSpawn() {
        this.applyConfiguredAttributes();

        double jumpStrength = this.getEntityAttribute(LOTRReflection.getHorseJumpStrength()).getAttributeValue();
        jumpStrength *= 0.5D;
        this.getEntityAttribute(LOTRReflection.getHorseJumpStrength()).setBaseValue(jumpStrength);

        this.setHealth(this.getMaxHealth());
    }

    public void writeEntityToNBT(NBTTagCompound nbt) {
        super.writeEntityToNBT(nbt);
        nbt.setInteger(NBT_MUMAKIL_MODE, this.getMumakilMode().getId());
        nbt.setInteger(
                NBT_FORMATION_ORIGIN,
                this.getFormationOrigin().getId()
        );
        nbt.setBoolean(
                NBT_HAS_MUMAKIL_HOWDAH,
                this.hasMumakilHowdahEquipped()
        );
        nbt.setFloat(NBT_SPEED_TRAIT, this.getMumakilSpeedTrait());
        if (this.hiredFormationOwnerUuid != null
                && this.getFormationOrigin()
                == MumakilFormationOrigin.PLAYER_HIRED) {
            nbt.setString(
                    NBT_HIRED_FORMATION_OWNER,
                    this.hiredFormationOwnerUuid.toString()
            );
        }
        if (this.mumakilInvasionId != null
                && this.getFormationOrigin()
                == MumakilFormationOrigin.INVASION_NEAR_HARAD) {
            nbt.setString(
                    NBT_MUMAK_INVASION_ID,
                    this.mumakilInvasionId.toString()
            );
        }
        nbt.setBoolean(NBT_BABY_GROWTH_INITIALIZED, this.babyGrowthInitialized);
        nbt.setLong(NBT_BABY_MELON_FED_UNTIL, this.babyMelonFedUntilTick);
        nbt.setLong(NBT_TAMED_BABY_MELON_HEAL_UNTIL, this.tamedBabyMelonHealUntilTick);
        nbt.setBoolean(NBT_CAPTIVE_BRED, this.captiveBred);
    }

    public void readEntityFromNBT(NBTTagCompound nbt) {
        super.readEntityFromNBT(nbt);

        ItemStack savedHowdah = this.getSavedMumakilHowdah(nbt);
        boolean savedHowdahEquipped =
                nbt.hasKey(NBT_HAS_MUMAKIL_HOWDAH)
                        ? nbt.getBoolean(NBT_HAS_MUMAKIL_HOWDAH)
                        : savedHowdah != null;
        this.formationOrigin = nbt.hasKey(NBT_FORMATION_ORIGIN)
                ? MumakilFormationOrigin.fromId(
                nbt.getInteger(NBT_FORMATION_ORIGIN)
        )
                : null;
        this.hiredFormationOwnerUuid = null;
        if (nbt.hasKey(NBT_HIRED_FORMATION_OWNER)) {
            try {
                this.hiredFormationOwnerUuid = UUID.fromString(
                        nbt.getString(NBT_HIRED_FORMATION_OWNER)
                );
            } catch (IllegalArgumentException ignored) {
                this.hiredFormationOwnerUuid = null;
            }
        }
        this.mumakilInvasionId = null;
        if (nbt.hasKey(NBT_MUMAK_INVASION_ID)) {
            try {
                this.mumakilInvasionId = UUID.fromString(
                        nbt.getString(NBT_MUMAK_INVASION_ID)
                );
            } catch (IllegalArgumentException ignored) {
                this.mumakilInvasionId = null;
            }
        }
        /*
         * Old worlds have no trait key and must retain exactly the historical
         * 1.00 movement factor. Loading never consumes random numbers.
         */
        this.mumakilSpeedTrait = nbt.hasKey(NBT_SPEED_TRAIT)
                ? clampSpeedTrait(nbt.getFloat(NBT_SPEED_TRAIT))
                : NORMAL_SPEED_TRAIT;
        this.mumakilSpeedTraitInitialized = true;
        this.babyGrowthInitialized = nbt.getBoolean(NBT_BABY_GROWTH_INITIALIZED);
        this.babyMelonFedUntilTick = nbt.getLong(NBT_BABY_MELON_FED_UNTIL);
        this.tamedBabyMelonHealUntilTick = nbt.getLong(NBT_TAMED_BABY_MELON_HEAL_UNTIL);
        // Old worlds and all non-bred Mumakil intentionally default to false.
        this.captiveBred = nbt.hasKey(NBT_CAPTIVE_BRED)
                && nbt.getBoolean(NBT_CAPTIVE_BRED);

        MumakilMode loadedMode = nbt.hasKey(NBT_MUMAKIL_MODE)
                ? MumakilMode.fromId(nbt.getInteger(NBT_MUMAKIL_MODE))
                : null;

        if (this.getEntityData().getBoolean(NBT_HIRED_WAR_MUMAKIL)) {
            loadedMode = MumakilMode.HIRED_WAR;
        }

        this.setMumakilMode(
                loadedMode == null ? this.inferNonHiredMumakilMode() : loadedMode
        );
        if (this.formationOrigin == null
                && this.isHiredWarMumakil()) {
            this.formationOrigin =
                    MumakilFormationOrigin.PLAYER_HIRED;
        }

        this.initializeBabyGrowthTimerIfNeeded();
        boolean baby = this.shouldUseBabyLifecycleState();
        this.applyMumakilLifecycleState(baby, false);
        this.babyLifecycleInitialized = true;
        this.wasBabyMumakil = baby;

        this.authoritativeMumakilHowdahEquipped = false;
        if (savedHowdahEquipped && !baby) {
            if (savedHowdah == null) {
                savedHowdah = new ItemStack(Main.mumakilHowdah);
            }
            this.setMumakilInventoryStack(1, savedHowdah);
            this.setMumakilHowdahEquipped(true);
        } else if (!this.worldObj.isRemote) {
            this.updateMumakilHowdahSyncState();
        }

        this.howdahRosterLoadGraceTicks =
                this.hasMumakilHowdahEquipped()
                        && MumakilHowdahArcherEventHandler
                        .isMarkedHowdahArcherCarrier(this)
                        ? HOWDAH_ROSTER_LOAD_GRACE_TICKS
                        : 0;
        MumakilHowdahArcherEventHandler
                .logLoadedMumakilHowdahState(this);

        if (this.angerWaveCooldownTicks <= 0 && this.angerWaveActiveTicks <= 0) {
            this.resetAngerWaveCooldown();
        }
    }

    private ItemStack getSavedMumakilHowdah(NBTTagCompound nbt) {
        if (nbt == null || !nbt.hasKey("ArmorItem", 10)) {
            return null;
        }

        ItemStack stack = ItemStack.loadItemStackFromNBT(
                nbt.getCompoundTag("ArmorItem")
        );
        return stack != null && stack.getItem() == Main.mumakilHowdah
                ? stack
                : null;
    }

    protected double clampChildHealth(double health) {
        return MathHelper.clamp_double(health, 100.0D, BABY_MAX_HEALTH);
    }

    protected double clampChildJump(double jump) {
        return MathHelper.clamp_double(jump, 0.2D, 0.8D);
    }

    protected double clampChildSpeed(double speed) {
        return MathHelper.clamp_double(speed, 0.18D, BABY_MOVEMENT_SPEED);
    }

    /*
     * MUMAKIL_ADULT_BREEDING_AND_FOV_TRANSITION_FIX_V1_1
     *
     * LOTREntityHorse.interact() enters love mode when a tame, full-grown
     * mount accepts the held breeding item. This explicit mode gate keeps
     * babies, wild adults, and hired-war Mumakil out of that path.
     */
    @Override
    public boolean isBreedingItem(ItemStack itemstack) {
        return this.getMumakilMode() == MumakilMode.ADULT_TAMED
                && itemstack != null
                && (itemstack.getItem() == Items.melon
                || Block.getBlockFromItem(itemstack.getItem())
                == Blocks.melon_block);
    }

    @Override
    public boolean canMateWith(EntityAnimal otherAnimal) {
        if (!(otherAnimal instanceof LOTREntityMumakil)) {
            return false;
        }

        LOTREntityMumakil otherMumakil =
                (LOTREntityMumakil)otherAnimal;

        return this.getMumakilMode() == MumakilMode.ADULT_TAMED
                && otherMumakil.getMumakilMode()
                == MumakilMode.ADULT_TAMED
                && super.canMateWith(otherAnimal);
    }

    @Override
    public EntityAgeable createChild(EntityAgeable otherParent) {
        EntityAgeable childEntity = super.createChild(otherParent);

        if (!(childEntity instanceof LOTREntityMumakil)) {
            return childEntity;
        }

        LOTREntityMumakil child =
                (LOTREntityMumakil)childEntity;

        // Offspring produced by the player-controlled tame breeding path
        // remain valuable mounts, but use the reduced captive-bred tusk table.
        child.captiveBred = true;

        if (otherParent instanceof LOTREntityMumakil) {
            LOTREntityMumakil otherMumakil =
                    (LOTREntityMumakil)otherParent;
            double parentAverage =
                    ((double)this.getMumakilSpeedTrait()
                            + (double)otherMumakil
                            .getMumakilSpeedTrait()) * 0.5D;
            double mutation =
                    this.rand.nextGaussian()
                            * SPEED_TRAIT_MUTATION_STANDARD_DEVIATION;
            child.setMumakilSpeedTrait(
                    (float)(parentAverage + mutation)
            );
        } else {
            child.setMumakilSpeedTrait(NORMAL_SPEED_TRAIT);
        }

        /*
         * The LOTR horse base marks offspring tame when both parents are
         * tame, but it does not copy an owner UUID. Preserve the first
         * available parent owner so the newborn becomes a usable BABY_TAMED
         * Mumakil.
         */
        String ownerId = this.func_152119_ch();

        if ((ownerId == null || ownerId.length() == 0)
                && otherParent instanceof LOTREntityMumakil) {
            ownerId = ((LOTREntityMumakil)otherParent)
                    .func_152119_ch();
        }

        if (ownerId != null && ownerId.length() > 0) {
            child.func_152120_b(ownerId);
            child.setHorseTamed(true);
        }

        /*
         * Saddle, howdah, archers, and hired-war state are intentionally not
         * inherited. The existing Mumakil lifecycle supplies the newborn's
         * 30-minute baby growth timer on its first update.
         */
        return child;
    }
// ---------------------------------------------------------------------
    // Main server tick update
    // ---------------------------------------------------------------------

    private void updatePlayerRiddenSpeedModifier() {
        IAttributeInstance movementAttribute = this.getEntityAttribute(
                SharedMonsterAttributes.movementSpeed
        );

        if (movementAttribute == null) {
            return;
        }

        AttributeModifier existingModifier = movementAttribute.getModifier(
                PLAYER_RIDDEN_SPEED_MODIFIER_UUID
        );
        boolean playerRiding = this.riddenByEntity instanceof EntityPlayer;

        if (playerRiding && existingModifier == null) {
            movementAttribute.applyModifier(
                    new AttributeModifier(
                            PLAYER_RIDDEN_SPEED_MODIFIER_UUID,
                            "Mumakil player-ridden speed",
                            PLAYER_RIDDEN_SPEED_MULTIPLIER - 1.0D,
                            2
                    )
            );
        } else if (!playerRiding && existingModifier != null) {
            movementAttribute.removeModifier(existingModifier);
        }
    }
    private void updateSafeTamedPlayerDismount() {
        if (this.worldObj.isRemote) {
            return;
        }

        if (this.riddenByEntity instanceof EntityPlayer) {
            this.lastMountedPlayerForSafeDismount =
                    (EntityPlayer)this.riddenByEntity;
            this.pendingSafeGroundDismountPlayer = null;
            this.pendingSafeGroundDismountTicks = 0;
            return;
        }

        if (this.lastMountedPlayerForSafeDismount != null) {
            EntityPlayer dismountedPlayer =
                    this.lastMountedPlayerForSafeDismount;
            this.lastMountedPlayerForSafeDismount = null;

            if (this.isTame()
                    && !dismountedPlayer.isDead
                    && dismountedPlayer.isEntityAlive()
                    && dismountedPlayer.worldObj == this.worldObj
                    && dismountedPlayer.ridingEntity == null
                    && this.getDistanceSqToEntity(dismountedPlayer) <= 1024.0D) {
                this.pendingSafeGroundDismountPlayer = dismountedPlayer;
                this.pendingSafeGroundDismountTicks = 5;
            }
        }

        if (this.pendingSafeGroundDismountPlayer == null) {
            return;
        }

        EntityPlayer pendingPlayer =
                this.pendingSafeGroundDismountPlayer;

        if (pendingPlayer.isDead
                || !pendingPlayer.isEntityAlive()
                || pendingPlayer.worldObj != this.worldObj
                || pendingPlayer.ridingEntity != null
                || this.getDistanceSqToEntity(pendingPlayer) > 1024.0D
                || this.pendingSafeGroundDismountTicks <= 0) {
            this.pendingSafeGroundDismountPlayer = null;
            this.pendingSafeGroundDismountTicks = 0;
            return;
        }

        /*
         * Reapply briefly because the normal horse/player dismount update can
         * restore the high seat position during the first tick.
         */
        this.placeDismountedPlayerBesideMumakil(pendingPlayer);
        --this.pendingSafeGroundDismountTicks;

        if (this.pendingSafeGroundDismountTicks <= 0) {
            this.pendingSafeGroundDismountPlayer = null;
        }
    }
    private void placeDismountedPlayerBesideMumakil(
            EntityPlayer player
    ) {
        float yawRadians = this.renderYawOffset
                * 3.1415927F / 180.0F;

        double forwardX = -MathHelper.sin(yawRadians);
        double forwardZ = MathHelper.cos(yawRadians);
        double sideX = MathHelper.cos(yawRadians);
        double sideZ = MathHelper.sin(yawRadians);

        double dismountDistance = Math.max(
                2.0D,
                this.width * 0.5D + player.width * 0.5D + 0.75D
        );

        double[][] offsets = new double[][] {
                {sideX * dismountDistance, sideZ * dismountDistance},
                {-sideX * dismountDistance, -sideZ * dismountDistance},
                {-forwardX * dismountDistance, -forwardZ * dismountDistance},
                {forwardX * dismountDistance, forwardZ * dismountDistance},
                {
                        (sideX - forwardX) * dismountDistance,
                        (sideZ - forwardZ) * dismountDistance
                },
                {
                        (-sideX - forwardX) * dismountDistance,
                        (-sideZ - forwardZ) * dismountDistance
                },
                {
                        (sideX + forwardX) * dismountDistance,
                        (sideZ + forwardZ) * dismountDistance
                },
                {
                        (-sideX + forwardX) * dismountDistance,
                        (-sideZ + forwardZ) * dismountDistance
                }
        };

        for (int i = 0; i < offsets.length; ++i) {
            double candidateX = this.posX + offsets[i][0];
            double candidateZ = this.posZ + offsets[i][1];

            if (this.tryPlaceDismountedPlayerOnGround(
                    player,
                    candidateX,
                    candidateZ
            )) {
                return;
            }
        }
    }

    private boolean tryPlaceDismountedPlayerOnGround(
            EntityPlayer player,
            double x,
            double z
    ) {
        int blockX = MathHelper.floor_double(x);
        int blockZ = MathHelper.floor_double(z);
        int baseY = MathHelper.floor_double(this.boundingBox.minY);

        for (int groundY = baseY + 2;
             groundY >= baseY - 5;
             --groundY) {
            Block groundBlock = this.worldObj.getBlock(
                    blockX,
                    groundY,
                    blockZ
            );

            AxisAlignedBB groundBox =
                    groundBlock.getCollisionBoundingBoxFromPool(
                            this.worldObj,
                            blockX,
                            groundY,
                            blockZ
                    );

            if (groundBox == null) {
                continue;
            }

            double standY = groundBox.maxY;
            double halfWidth = player.width * 0.5D;

            AxisAlignedBB playerBox = AxisAlignedBB.getBoundingBox(
                    x - halfWidth,
                    standY,
                    z - halfWidth,
                    x + halfWidth,
                    standY + player.height,
                    z + halfWidth
            );

            if (!this.worldObj.getCollidingBoundingBoxes(
                    player,
                    playerBox
            ).isEmpty()) {
                continue;
            }

            player.setPosition(x, standY, z);

            if (player instanceof EntityPlayerMP) {
                EntityPlayerMP serverPlayer = (EntityPlayerMP)player;
                serverPlayer.playerNetServerHandler.setPlayerLocation(
                        x,
                        standY,
                        z,
                        player.rotationYaw,
                        player.rotationPitch
                );
            }

            player.motionX = 0.0D;
            player.motionY = 0.0D;
            player.motionZ = 0.0D;
            player.fallDistance = 0.0F;
            player.onGround = true;
            player.velocityChanged = true;
            return true;
        }

        return false;
    }
    public void onLivingUpdate() {
        this.updatePlayerRiddenSpeedModifier();

        /*
         * BABY_TAMED_RIDING_EQUIPMENT_FIX_V1
         * EntityHorse's successful taming transition can clear the rider.
         * Remember the player who was actively taming this wild baby so the
         * successful attempt can remain mounted.
         */
        EntityPlayer successfulBabyTamingRider = null;
        boolean wasUntamedBabyTamingAttempt = false;

        if (!this.worldObj.isRemote
                && this.getMumakilMode() == MumakilMode.BABY_WILD
                && !this.isTame()
                && this.riddenByEntity instanceof EntityPlayer) {
            successfulBabyTamingRider = (EntityPlayer)this.riddenByEntity;
            wasUntamedBabyTamingAttempt = true;
        }
        long superPerfStart = !this.worldObj.isRemote && MumakilPerformanceTracker.isEnabled()
                ? MumakilPerformanceTracker.startTimer()
                : 0L;

        try {
            super.onLivingUpdate();
        } finally {
            if (!this.worldObj.isRemote && MumakilPerformanceTracker.isEnabled()) {
                MumakilPerformanceTracker.recordMountSuperLiving(
                        this,
                        System.nanoTime() - superPerfStart
                );
            }
        }
        this.updatePlayerRiddenLocomotionPhase();
        this.updateMumakilFootfallPhase();

        if (!this.worldObj.isRemote
                && this.getFormationOrigin()
                == MumakilFormationOrigin.INVASION_NEAR_HARAD
                && (this.ticksExisted + this.getEntityId()) % 100 == 0) {
            UUID invasionId = this.getMumakilInvasionId();
            LOTREntityInvasionSpawner spawner =
                    invasionId == null
                            ? null
                            : LOTREntityInvasionSpawner
                            .locateInvasionNearby(this, invasionId);
            if (spawner == null) {
                Entity capturedDriver = this.riddenByEntity;
                MumakilWarFormationFactory
                        .removeNaturalFormationMembers(
                                this,
                                capturedDriver
                        );
                this.setDead();
                return;
            }
        }

        if (!this.worldObj.isRemote
                && wasUntamedBabyTamingAttempt
                && successfulBabyTamingRider != null
                && this.isTame()) {
            this.setMumakilMode(MumakilMode.BABY_TAMED);
            this.babyMelonFedUntilTick = 0L;

            if (!successfulBabyTamingRider.isDead
                    && successfulBabyTamingRider.isEntityAlive()
                    && successfulBabyTamingRider.ridingEntity == null
                    && this.riddenByEntity == null) {
                successfulBabyTamingRider.mountEntity(this);
            }
        }
        this.updateSafeTamedPlayerDismount();

        if (this.worldObj.isRemote) {
            this.applyMumakilPhysicalSize(this.isChild());
        } else {
            this.updateMumakilLifecycle();
            this.updateMumakilAmbientAnimationSounds();
            this.faceAIMovementDirection();
        }

        this.stabilizeIdleYaw();

        this.prevMumakilStrikeAnimationTicks = this.mumakilStrikeAnimationTicks;

        if (this.mumakilStrikeAnimationTicks > 0) {
            --this.mumakilStrikeAnimationTicks;
        }

        if (!this.worldObj.isRemote) {
            if (this.getMumakilMode() == MumakilMode.ADULT_WILD
                    && !this.isTame()
                    && this.riddenByEntity instanceof EntityPlayer) {
                EntityPlayer invalidAdultRider =
                        (EntityPlayer)this.riddenByEntity;
                invalidAdultRider.mountEntity(null);
                this.placeDismountedPlayerBesideMumakil(
                        invalidAdultRider
                );
            }
            if (DEBUG_MUMAKIL_MODE && this.ticksExisted % 100 == 0) {
                System.out.println(
                        "[LOTRMoreMobs] Mumakil mode"
                                + " entity=" + this.getEntityId()
                                + " mode=" + this.getMumakilMode()
                                + " child=" + this.isChild()
                                + " tame=" + this.isTame()
                                + " belongsToNPC=" + this.getBelongsToNPC()
                                + " rider=" + (this.riddenByEntity == null
                                ? "none"
                                : this.riddenByEntity.getClass().getSimpleName())
                );
            }

            this.updateMumakilHowdahSyncState();

            if (this.tuskAttackCooldownTicks > 0) {
                --this.tuskAttackCooldownTicks;
            }

            this.updateAngerWave();
            this.updateTerritorialWarningAndHerdRegroup();
            this.validateWildAttackTargetLifecycle();
            this.tryAcquireWildMobTarget();
            this.tryTuskReachAttack();
            if (!MumakilPerformanceTracker.DEBUG_DISABLE_MUMAKIL_TREE_CLEARING) {
                long serverTreeTimingStart =
                        MumakilServerPerformanceDiagnostics
                                .startTimer(this.worldObj);
                boolean trackTreePerformance =
                        MumakilPerformanceTracker.isEnabled();
                long treePerfStart = trackTreePerformance
                        ? MumakilPerformanceTracker.startTimer()
                        : 0L;
                try {
                    this.clearAggroObstaclesForMovement();
                } finally {
                    MumakilServerPerformanceDiagnostics
                            .recordLeafBreakingScan(
                                    this.worldObj,
                                    System.nanoTime()
                                            - serverTreeTimingStart
                            );
                    if (trackTreePerformance) {
                        MumakilPerformanceTracker.recordTreeScan(
                                this,
                                System.nanoTime() - treePerfStart
                        );
                    }
                }
            }
            this.applyTrampleDamage();

            if (!this.isBabyMumakil()
                    && this.riddenByEntity instanceof EntityLivingBase) {
                float momentum = MathHelper.sqrt_double(
                        this.motionX * this.motionX
                                + this.motionZ * this.motionZ
                );
                this.setSprinting(momentum > 0.18F);
            }

            if (MumakilPerformanceTracker.isEnabled()) {
                MumakilPerformanceTracker.reportIfDue(this);
            }
        }
    }

    private void updatePlayerRiddenLocomotionPhase() {
        float rawPhase = this.limbSwing;
        boolean playerRidden = this.riddenByEntity instanceof EntityPlayer;

        if (!this.playerRiddenLocomotionInitialized) {
            this.playerRiddenLocomotionPhase = rawPhase;
            this.prevPlayerRiddenLocomotionPhase = rawPhase;
            this.lastRawLocomotionPhase = rawPhase;
            this.playerRiddenLocomotionInitialized = true;
            this.wasPlayerRiddenForLocomotion = playerRidden;
            return;
        }

        this.prevPlayerRiddenLocomotionPhase =
                this.playerRiddenLocomotionPhase;

        if (playerRidden) {
            if (!this.wasPlayerRiddenForLocomotion) {
                this.playerRiddenLocomotionPhase = rawPhase;
                this.prevPlayerRiddenLocomotionPhase = rawPhase;
            } else {
                this.playerRiddenLocomotionPhase +=
                        (rawPhase - this.lastRawLocomotionPhase) * 0.5F;
            }
        } else {
            /*
             * AI-controlled rendering continues to use the native phase
             * exactly, without inheriting a ridden animation offset.
             */
            this.playerRiddenLocomotionPhase = rawPhase;
            this.prevPlayerRiddenLocomotionPhase = rawPhase;
        }

        this.lastRawLocomotionPhase = rawPhase;
        this.wasPlayerRiddenForLocomotion = playerRidden;
    }

    private void updateMumakilFootfallPhase() {
        boolean playerRidden = this.riddenByEntity instanceof EntityPlayer;
        double deltaX = this.posX - this.prevPosX;
        double deltaZ = this.posZ - this.prevPosZ;
        boolean translating = deltaX * deltaX + deltaZ * deltaZ > 0.0004D;

        /*
         * Match the same locomotion phase used by LOTRRenderMumakilGeo.
         */
        float locomotionPhase = playerRidden
                ? this.playerRiddenLocomotionPhase
                : this.limbSwing;

        /*
         * Unridden calves use a three-times-faster visible walking phase.
         * Player-ridden calves intentionally use the normal ridden phase.
         */
        if (this.isChild() && !playerRidden) {
            locomotionPhase *= 3.0F;
        }

        /*
         * Renderer uses:
         *
         *     walkPhase = limbSwing * 0.55F;
         *
         * The two diagonal leg pairs are PI radians apart, so crossing
         * each PI interval represents the next alternating footfall.
         */
        float walkPhase = locomotionPhase * 0.55F;

        boolean walking = this.limbSwingAmount >= 0.15F && translating;

        if (!walking) {
            this.mumakilFootfallTrackingInitialized = false;
            return;
        }

        int footfallIndex = MathHelper.floor_float(
                walkPhase / (float)Math.PI
        );

        /*
         * Starting movement only establishes our position in the cycle.
         * Do not produce an immediate stomp merely because the Mumak
         * transitioned from idle to walking.
         */
        if (!this.mumakilFootfallTrackingInitialized) {
            this.lastMumakilFootfallIndex = footfallIndex;
            this.mumakilFootfallTrackingInitialized = true;
            return;
        }

        if (footfallIndex != this.lastMumakilFootfallIndex
                && (this.lastMumakilFootfallTick == Integer.MIN_VALUE
                || this.ticksExisted - this.lastMumakilFootfallTick >= 6)) {
            this.lastMumakilFootfallIndex = footfallIndex;
            this.lastMumakilFootfallTick = this.ticksExisted;

            if (!this.worldObj.isRemote) {
                float basePitch = this.isBabyMumakil() ? 1.25F : 1.00F;
                float pitchVariation =
                        (this.rand.nextFloat() - this.rand.nextFloat()) * 0.08F;

                float stepVolume = this.isBabyMumakil() ? 0.5F : 0.8F;

                this.worldObj.playSoundAtEntity(
                        this,
                        "lotrmoremobs:mumakil.step",
                        0.8F,
                        basePitch + pitchVariation
                );
            }
        }
    }

    public float getPlayerRiddenLocomotionPhase(float partialTicks) {
        if (!this.playerRiddenLocomotionInitialized
                || !this.wasPlayerRiddenForLocomotion) {
            return this.limbSwing
                    - this.limbSwingAmount
                    * (1.0F - MathHelper.clamp_float(
                    partialTicks,
                    0.0F,
                    1.0F
            ));
        }

        float interpolation = MathHelper.clamp_float(
                partialTicks,
                0.0F,
                1.0F
        );
        return this.prevPlayerRiddenLocomotionPhase
                + (this.playerRiddenLocomotionPhase
                - this.prevPlayerRiddenLocomotionPhase)
                * interpolation;
    }


    // ---------------------------------------------------------------------
    // Direct melee, tusk attack, and projectile damage
    // ---------------------------------------------------------------------

    @Override
    public void setAttackTarget(EntityLivingBase target) {
        if (target != null && this.isBabyMumakil()) {
            return;
        }

        if (target != null
                && this.isHiredWarMumakil()
                && this.hasLivingNPCCombatDriver()) {
            EntityLivingBase driverTarget =
                    ((LOTREntityNPC)this.riddenByEntity).getAttackTarget();
            EntityLivingBase formationThreat = this.getRecentFormationThreat();
            if (target != driverTarget && target != formationThreat) {
                return;
            }
        }

        EntityLivingBase previousTarget = this.getAttackTarget();

        if (MumakilPerformanceTracker.isEnabled()
                && this.worldObj != null
                && !this.worldObj.isRemote
                && previousTarget != target) {
            MumakilPerformanceTracker.recordTargetChange(this);
        }

        super.setAttackTarget(target);
    }

    public boolean attackEntityAsMob(Entity target) {
        if (this.isBabyMumakil()
                || this.riddenByEntity instanceof EntityPlayer
                || this.tuskAttackCooldownTicks > 0) {
            return false;
        }

        boolean attacked = super.attackEntityAsMob(target);
        if (attacked && !this.worldObj.isRemote) {
            this.tuskAttackCooldownTicks = TUSK_ATTACK_COOLDOWN_TICKS;
            this.recordSuccessfulTuskHit();
            this.startMumakilStrikeAnimation();
            this.applyMumakilHeavyKnockback(target, 1.5F, 0.45F);

            this.applyMumakilStrikeAOEDamage(target, TUSK_AOE_DAMAGE);
        }

        return attacked;
    }

    public boolean attackEntityFrom(DamageSource source, float amount) {
        if (this.shouldBlockOwnHowdahArcherArrow(source)) {
            return true;
        }

        boolean arrowDamage = this.isMumakilArrowDamage(source);
        boolean damaged = super.attackEntityFrom(source, amount);

        if (damaged
                && !this.worldObj.isRemote
                && amount > 0.0F
                && this.getMumakilMode() == MumakilMode.BABY_WILD) {
            this.alertNearbyWildAdultsToBabyAttack(source);
        }

        if (damaged && !this.worldObj.isRemote && amount > 0.0F) {
            EntityLivingBase formationAttacker =
                    this.resolveTamedRetaliationAttacker(source);
            if (this.isWarCombatFormation()
                    && this.isValidFormationRetaliationAttacker(
                    formationAttacker
            )) {
                this.recordRecentFormationThreat(formationAttacker);
                this.setRevengeTarget(formationAttacker);
                this.setAttackTarget(formationAttacker);
            }
            this.retaliateAsTamedAdult(source);
        }

        if (damaged && !this.worldObj.isRemote && arrowDamage) {
            this.hurtResistantTime = Math.min(this.hurtResistantTime, PROJECTILE_HURT_RESISTANT_TICKS);
        }

        if (damaged && !this.worldObj.isRemote && amount > 0.0F && !this.isDead) {
            this.playMumakilNormalHitSound();
        }

        if (!damaged && this.shouldConsumeBlockedMumakilArrow(source, amount)) {
            return true;
        }

        return damaged;
    }

    private void retaliateAsTamedAdult(DamageSource source) {
        if (this.isDead
                || !this.isEntityAlive()
                || !this.isTame()
                || this.isChild()) {
            return;
        }

        EntityLivingBase attacker = this.resolveTamedRetaliationAttacker(
                source
        );
        if (!this.isValidTamedRetaliationAttacker(attacker)) {
            this.clearExcludedTamedRetaliationTarget(attacker);
            return;
        }

        /*
         * EntityLivingBase records the revenge entity, but a tame LOTR horse
         * does not reliably promote that entity to its active attack target.
         * Keep navigation and combat in the established AI by setting both
         * pieces of target state after accepted damage.
         */
        this.setRevengeTarget(attacker);
        this.setAttackTarget(attacker);
    }

    private void clearExcludedTamedRetaliationTarget(
            EntityLivingBase attacker
    ) {
        if (attacker == null) {
            return;
        }

        /*
         * super.attackEntityFrom() has already recorded source.getEntity() as
         * the revenge entity. Remove only this excluded attacker; preserve an
         * unrelated, already active combat target.
         */
        if (this.getAITarget() == attacker) {
            this.setRevengeTarget(null);
        }
        if (this.getAttackTarget() == attacker) {
            this.setAttackTarget(null);
        }
    }

    private EntityLivingBase resolveTamedRetaliationAttacker(
            DamageSource source
    ) {
        if (source == null) {
            return null;
        }

        Entity responsibleEntity = source.getEntity();
        if (responsibleEntity instanceof EntityLivingBase) {
            return (EntityLivingBase)responsibleEntity;
        }

        Entity directDamageEntity = source.getSourceOfDamage();
        if (directDamageEntity instanceof EntityArrow) {
            responsibleEntity =
                    ((EntityArrow)directDamageEntity).shootingEntity;
        } else if (directDamageEntity instanceof IThrowableEntity) {
            responsibleEntity =
                    ((IThrowableEntity)directDamageEntity).getThrower();
        } else if (directDamageEntity instanceof EntityThrowable) {
            responsibleEntity =
                    ((EntityThrowable)directDamageEntity).getThrower();
        } else if (directDamageEntity instanceof EntityFireball) {
            responsibleEntity =
                    ((EntityFireball)directDamageEntity).shootingEntity;
        } else if (directDamageEntity instanceof EntityLivingBase) {
            responsibleEntity = directDamageEntity;
        }

        return responsibleEntity instanceof EntityLivingBase
                ? (EntityLivingBase)responsibleEntity
                : null;
    }

    private boolean isValidTamedRetaliationAttacker(
            EntityLivingBase attacker
    ) {
        if (attacker == null
                || attacker == this
                || attacker.isDead
                || !attacker.isEntityAlive()
                || attacker.worldObj != this.worldObj) {
            return false;
        }

        if (attacker instanceof EntityPlayer
                && this.isOwner((EntityPlayer)attacker)) {
            return false;
        }

        return !this.isOwnValidatedFormationMember(attacker);
    }

    private boolean isValidFormationRetaliationAttacker(
            EntityLivingBase attacker
    ) {
        if (!this.isValidTamedRetaliationAttacker(attacker)) {
            return false;
        }

        if (attacker instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer)attacker;
            if (player.capabilities.isCreativeMode) {
                return false;
            }

            if (this.riddenByEntity instanceof LOTREntityNPC) {
                UUID hiringPlayerId = ((LOTREntityNPC)this.riddenByEntity)
                        .hiredNPCInfo.getHiringPlayerUUID();
                if (hiringPlayerId != null
                        && hiringPlayerId.equals(player.getUniqueID())) {
                    return false;
                }
            }
        }

        return true;
    }

    private boolean isOwnValidatedFormationMember(
            EntityLivingBase attacker
    ) {
        boolean ownDriver = attacker == this.riddenByEntity
                && MumakilDriverControlEventHandler
                .isFullyAttachedMumakilDriver(attacker);
        return ownDriver || this.isOwnAttachedHowdahArcher(attacker);
    }

    private boolean isMumakilArrowDamage(DamageSource source) {
        return source != null
                && (source.isProjectile() || source.getSourceOfDamage() instanceof EntityArrow);
    }

    private boolean shouldBlockOwnHowdahArcherArrow(DamageSource source) {
        return source != null
                && this.isHiredWarMumakil()
                && this.getMumakilDamageArrow(source) != null
                && this.isOwnAttachedHowdahArcher(this.getMumakilArrowShooter(source));
    }

    private EntityArrow getMumakilDamageArrow(DamageSource source) {
        Entity damageEntity = source.getSourceOfDamage();
        return damageEntity instanceof EntityArrow ? (EntityArrow)damageEntity : null;
    }

    private Entity getMumakilArrowShooter(DamageSource source) {
        Entity shooter = source.getEntity();
        if (shooter instanceof LOTREntityMumakilHowdahArcher) {
            return shooter;
        }

        EntityArrow arrow = this.getMumakilDamageArrow(source);
        return arrow == null ? null : arrow.shootingEntity;
    }

    private boolean isOwnAttachedHowdahArcher(Entity shooter) {
        if (!(shooter instanceof LOTREntityMumakilHowdahArcher)) {
            return false;
        }

        LOTREntityMumakilHowdahArcher archer =
                (LOTREntityMumakilHowdahArcher)shooter;
        return MumakilHowdahArcherEventHandler
                .getFullyValidatedAttachedArcherParent(archer)
                == this;
    }

    private boolean shouldConsumeBlockedMumakilArrow(DamageSource source, float amount) {
        return this.isMumakilArrowDamage(source)
                && amount > 0.0F
                && !this.worldObj.isRemote
                && !this.isDead
                && this.getHealth() > 0.0F
                && !this.isEntityInvulnerable();
    }

    /**
     * MUMAKIL_SAME_SPECIES_SEPARATION_V1_2
     *
     * Returning true keeps Minecraft's collision callback active. External
     * movement is still blocked by addVelocity() and knockBack() below.
     */
    @Override
    public boolean canBePushed() {
        return true;
    }

    @Override
    public void applyEntityCollision(Entity entity) {
        if (entity instanceof LOTREntityMumakil
                && entity != this
                && !this.isDead
                && !entity.isDead
                && this.boundingBox != null
                && entity.boundingBox != null
                && this.boundingBox.intersectsWith(
                entity.boundingBox
        )) {
            LOTREntityMumakil other =
                    (LOTREntityMumakil)entity;

            /*
             * A touching pair can be visited from both entity update loops.
             * Only the lower entity ID resolves it so the response is not
             * applied twice.
             */
            if (this.getEntityId() > other.getEntityId()) {
                return;
            }

            double overlapX = Math.min(
                    this.boundingBox.maxX,
                    other.boundingBox.maxX
            ) - Math.max(
                    this.boundingBox.minX,
                    other.boundingBox.minX
            );
            double overlapZ = Math.min(
                    this.boundingBox.maxZ,
                    other.boundingBox.maxZ
            ) - Math.max(
                    this.boundingBox.minZ,
                    other.boundingBox.minZ
            );

            if (overlapX <= 0.0D || overlapZ <= 0.0D) {
                return;
            }

            double directionX = 0.0D;
            double directionZ = 0.0D;
            double overlapDepth;

            /*
             * Separate along the shallowest horizontal overlap. This is the
             * shortest route out of the intersection.
             */
            if (overlapX < overlapZ) {
                directionX = other.posX >= this.posX
                        ? 1.0D
                        : -1.0D;
                overlapDepth = overlapX;
            } else if (overlapZ < overlapX) {
                directionZ = other.posZ >= this.posZ
                        ? 1.0D
                        : -1.0D;
                overlapDepth = overlapZ;
            } else {
                /*
                 * Deterministic tie-breaker for perfectly centered overlaps.
                 */
                int mixedIds = this.getEntityId() * 31
                        + other.getEntityId() * 17;

                if ((mixedIds & 1) == 0) {
                    directionX = (mixedIds & 2) == 0
                            ? 1.0D
                            : -1.0D;
                } else {
                    directionZ = (mixedIds & 2) == 0
                            ? 1.0D
                            : -1.0D;
                }

                overlapDepth = overlapX;
            }

            /*
             * Increase the correction with overlap depth, but cap it so
             * grouped Mumakil spread smoothly instead of launching apart.
             */
            double separationSpeed = Math.min(
                    0.16D,
                    0.045D + overlapDepth * 0.018D
            );

            this.applyInternalMumakilSeparationVelocity(
                    -directionX * separationSpeed,
                    -directionZ * separationSpeed
            );
            other.applyInternalMumakilSeparationVelocity(
                    directionX * separationSpeed,
                    directionZ * separationSpeed
            );
            return;
        }

        /*
         * Smaller entities keep normal collision handling. This Mumakil
         * ignores the reciprocal push through addVelocity(), while the
         * smaller entity may still be moved out of its hitbox.
         */
        super.applyEntityCollision(entity);
    }

    private void applyInternalMumakilSeparationVelocity(
            double x,
            double z
    ) {
        /*
         * Deliberately bypass this class's external-velocity block.
         */
        super.addVelocity(x, 0.0D, z);
        this.velocityChanged = true;
    }
    @Override
    public void addVelocity(double x, double y, double z) {
        // External entities cannot add velocity to the Mumakil.
    }

    @Override
    public void knockBack(
            Entity attacker,
            float strength,
            double xRatio,
            double zRatio
    ) {
        // Damage lands without moving the Mumakil.
    }

    private void tryTuskReachAttack() {
        if (this.isBabyMumakil()
                || this.riddenByEntity instanceof EntityPlayer
                || this.tuskAttackCooldownTicks > 0) {
            return;
        }

        EntityLivingBase target = this.getAttackTarget();
        if (target == null || !this.canTuskAttackTarget(target)) {
            return;
        }

        if (this.getDistanceSqToEntity(target) > TUSK_ATTACK_RANGE * TUSK_ATTACK_RANGE) {
            return;
        }

        if (!this.isTuskTargetInFront(target)) {
            return;
        }

        if (!this.canEntityBeSeen(target)) {
            return;
        }

        if (target.attackEntityFrom(DamageSource.causeMobDamage(this), (float)ATTACK_DAMAGE)) {
            this.tuskAttackCooldownTicks = TUSK_ATTACK_COOLDOWN_TICKS;
            this.recordSuccessfulTuskHit();
            this.startMumakilStrikeAnimation();
            this.applyMumakilHeavyKnockback(target, 1.75F, 0.5F);

            this.applyMumakilStrikeAOEDamage(target, TUSK_AOE_DAMAGE);
        }
    }

    private void recordSuccessfulTuskHit() {
        ++this.successfulTuskAttackSequence;
        if (this.successfulTuskAttackSequence == Integer.MIN_VALUE) {
            this.successfulTuskAttackSequence = 1;
        }
    }

    private boolean applyMumakilStrikeAOEDamage(Entity primaryTarget, float damage) {
        if (this.worldObj.isRemote || !(primaryTarget instanceof EntityLivingBase)) {
            return false;
        }

        EntityLivingBase impactTarget = (EntityLivingBase)primaryTarget;
        AxisAlignedBB aoeBox = impactTarget.boundingBox.expand(
                TUSK_AOE_RADIUS,
                TUSK_AOE_VERTICAL_RANGE,
                TUSK_AOE_RADIUS
        );

        List nearby = this.worldObj.getEntitiesWithinAABB(EntityLivingBase.class, aoeBox);
        boolean hitAny = false;

        for (int i = 0; i < nearby.size(); ++i) {
            EntityLivingBase target = (EntityLivingBase)nearby.get(i);

            if (target == primaryTarget || !this.canTuskAttackTarget(target)) {
                continue;
            }

            /*
             * Keep the splash roughly circular around the impact point.
             * The expanded AABB finds candidates cheaply, then this radius check
             * prevents weird square-corner hits.
             */
            double dx = target.posX - impactTarget.posX;
            double dz = target.posZ - impactTarget.posZ;
            if (dx * dx + dz * dz > TUSK_AOE_RADIUS * TUSK_AOE_RADIUS) {
                continue;
            }

            if (target.attackEntityFrom(DamageSource.causeMobDamage(this), damage)) {
                this.applyMumakilHeavyKnockback(
                        target,
                        TUSK_AOE_KNOCKBACK_HORIZONTAL,
                        TUSK_AOE_KNOCKBACK_VERTICAL
                );
                hitAny = true;
            }
        }

        return hitAny;
    }

    private boolean canTuskAttackTarget(EntityLivingBase target) {
        if (MumakilPerformanceTracker.isEnabled()) {
            MumakilPerformanceTracker.recordTuskCandidateCheck(this);
        }

        if (target == this
                || target == this.riddenByEntity
                || target instanceof LOTREntityMumakil
                || !target.isEntityAlive()
                || target.riddenByEntity != null) {
            return false;
        }

        if (target instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer)target;
            if (player.capabilities.isCreativeMode || this.isOwner(player)) {
                return false;
            }
        }

        if (target instanceof EntityTameable && ((EntityTameable)target).isTamed()) {
            return false;
        }

        if (target instanceof EntityHorse && ((EntityHorse)target).isTame()) {
            return false;
        }

        if (target instanceof LOTREntityNPC) {
            LOTREntityNPC npc = (LOTREntityNPC)target;
            if (npc.hiredNPCInfo.isActive) {
                return false;
            }
        }

        if (this.riddenByEntity instanceof EntityPlayer
                && !LOTRMod.canPlayerAttackEntity((EntityPlayer)this.riddenByEntity, target, false)) {
            return false;
        }

        if (this.riddenByEntity instanceof EntityCreature
                && !LOTRMod.canNPCAttackEntity((EntityCreature)this.riddenByEntity, target, false)) {
            return false;
        }

        return true;
    }

    private boolean isTuskTargetInFront(EntityLivingBase target) {
        double deltaX = target.posX - this.posX;
        double deltaZ = target.posZ - this.posZ;
        double horizontalDistance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
        if (horizontalDistance <= TUSK_ATTACK_CLOSE_RANGE) {
            return true;
        }

        Vec3 look = this.getLookVec();
        double lookX = look.xCoord;
        double lookZ = look.zCoord;
        double lookLength = Math.sqrt(lookX * lookX + lookZ * lookZ);

        if (lookLength > 1.0E-4D) {
            lookX /= lookLength;
            lookZ /= lookLength;
        } else {
            lookX = (double)(-MathHelper.sin(this.rotationYaw * (float)Math.PI / 180.0F));
            lookZ = (double)(MathHelper.cos(this.rotationYaw * (float)Math.PI / 180.0F));
        }

        return (lookX * deltaX + lookZ * deltaZ) / horizontalDistance >= TUSK_ATTACK_FRONT_CONE_DOT;
    }

    private void applyMumakilHeavyKnockback(Entity target, float horizontalStrength, float verticalStrength) {
        if (!(target instanceof EntityLivingBase)) {
            return;
        }

        double deltaX = target.posX - this.posX;
        double deltaZ = target.posZ - this.posZ;
        double distance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);

        if (distance > 1.0E-4D) {
            deltaX /= distance;
            deltaZ /= distance;
        } else {
            deltaX = (double)(-MathHelper.sin(this.rotationYaw * (float)Math.PI / 180.0F));
            deltaZ = (double)(MathHelper.cos(this.rotationYaw * (float)Math.PI / 180.0F));
        }

        target.addVelocity(deltaX * (double)horizontalStrength, (double)verticalStrength, deltaZ * (double)horizontalStrength);
        target.velocityChanged = true;
    }


    // ---------------------------------------------------------------------
    // Trample damage
    // ---------------------------------------------------------------------

    private void applyTrampleDamage() {

        if (this.isBabyMumakil()

                || this.worldObj.isRemote

                || this.riddenByEntity instanceof EntityPlayer

                || this.isTerritorialWarningActive()

                || this.isWildHerdRegroupActive()

                || this.ticksExisted % TRAMPLE_SCAN_INTERVAL != 0) {

            return;

        }



        boolean trampleActive = this.isWildMumakil()

                || this.isMountEnraged()

                || this.riddenByEntity != null;



        if (!trampleActive) {

            return;

        }



        /*

         * Use actual horizontal displacement from the previous tick rather

         * than stored motion. This prevents residual velocity, collision

         * jitter, or an idle entity with stale motion values from trampling.

         */

        double movedX = this.posX - this.prevPosX;

        double movedZ = this.posZ - this.prevPosZ;

        float actualMovement = MathHelper.sqrt_double(

                movedX * movedX + movedZ * movedZ

        );



        if (actualMovement < TRAMPLE_MIN_SPEED) {
            return;

        }

        if (this.ticksExisted < this.nextTrampleDamageTick) {
            return;
        }

        double directionX = movedX / (double)actualMovement;

        double directionZ = movedZ / (double)actualMovement;

        AxisAlignedBB trampleBox = this.boundingBox

                .expand(0.85D, 0.5D, 0.85D)

                .addCoord(

                        directionX * 1.5D,

                        -0.35D,

                        directionZ * 1.5D

                );



        boolean trackPerformance =
                MumakilPerformanceTracker.isEnabled();
        long perfStart = trackPerformance
                ? MumakilPerformanceTracker.startTimer()
                : 0L;
        long serverCollisionStart =
                MumakilServerPerformanceDiagnostics.startTimer(
                        this.worldObj
                );

        List nearby = this.worldObj.getEntitiesWithinAABB(

                EntityLivingBase.class,

                trampleBox

        );
        MumakilServerPerformanceDiagnostics.recordCollisionScan(
                this.worldObj,
                System.nanoTime() - serverCollisionStart
        );



        if (trackPerformance) {

            MumakilPerformanceTracker.recordTrampleScan(

                    this,

                    nearby.size(),

                    System.nanoTime() - perfStart

            );

        }



        for (int i = 0; i < nearby.size(); ++i) {

            EntityLivingBase target =

                    (EntityLivingBase)nearby.get(i);



            if (!this.canTrample(target)) {

                continue;

            }



            /*

             * Normal mob damage can apply vanilla knockback. Preserve and

             * restore the target's existing velocity so trample damage adds

             * no horizontal or vertical knockback of its own.

             */

            double previousMotionX = target.motionX;

            double previousMotionY = target.motionY;

            double previousMotionZ = target.motionZ;



            if (!target.attackEntityFrom(

                    DamageSource.causeMobDamage(this),

                    TRAMPLE_DAMAGE

            )) {

                continue;

            }



            target.motionX = previousMotionX;

            target.motionY = previousMotionY;

            target.motionZ = previousMotionZ;

            target.velocityChanged = true;



            /*

             * One successful target consumes the global cooldown. A crowd

             * entering the Mumakil's hitbox is therefore not all damaged at

             * once, and another trample cannot occur for five seconds.

             */

            this.nextTrampleDamageTick =

                    this.ticksExisted + TRAMPLE_COOLDOWN_TICKS;

            this.playMumakilHitSound();

            break;

        }

    }



    private boolean canTrample(EntityLivingBase target) {
        if (MumakilPerformanceTracker.isEnabled()) {
            MumakilPerformanceTracker.recordTrampleCandidateCheck(this);
        }

        if (target == this
                || target == this.riddenByEntity
                || target instanceof LOTREntityMumakil
                || target.riddenByEntity != null
                || !target.isEntityAlive()) {
            return false;
        }

        if (target instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer)target;
            if (player.capabilities.isCreativeMode || this.isOwner(player)) {
                return false;
            }
        }

        if (target instanceof EntityTameable && ((EntityTameable)target).isTamed()) {
            return false;
        }

        if (target instanceof EntityHorse && ((EntityHorse)target).isTame()) {
            return false;
        }

        if (target instanceof LOTREntityNPC) {
            LOTREntityNPC npc = (LOTREntityNPC)target;
            if (npc.hiredNPCInfo.isActive) {
                return false;
            }

            LOTRFaction targetFaction = LOTRMod.getNPCFaction(npc);
            if (!LOTRFaction.NEAR_HARAD.isBadRelation(targetFaction)) {
                return false;
            }
        }

        if (this.riddenByEntity instanceof EntityPlayer
                && !LOTRMod.canPlayerAttackEntity((EntityPlayer)this.riddenByEntity, target, false)) {
            return false;
        }

        if (this.riddenByEntity instanceof EntityCreature
                && !LOTRMod.canNPCAttackEntity((EntityCreature)this.riddenByEntity, target, false)) {
            return false;
        }

        return true;
    }

    public boolean isTamedByPlayer(EntityPlayer player) {
        return player != null && this.isOwner(player);
    }

    private boolean isOwner(EntityPlayer player) {
        if (!this.isTame()) {
            return false;
        }

        String ownerId = this.func_152119_ch();
        if (ownerId != null
                && ownerId.length() > 0
                && ownerId.equals(player.getUniqueID().toString())) {
            return true;
        }

        UUID hiredOwner = this.getPlayerHiredFormationOwnerUuid();
        return hiredOwner != null
                && hiredOwner.equals(player.getUniqueID());
    }




    // ---------------------------------------------------------------------
    // Tree / obstacle clearing
    // ---------------------------------------------------------------------

    private void clearAggroObstaclesForMovement() {
        this.clearAggroObstaclesForMovement(false);
    }

    private void clearAggroObstaclesForMovement(boolean force) {
        if (this.isBabyMumakil()
                || this.worldObj.isRemote) {
            return;
        }

        int regionX = MathHelper.floor_double(this.posX);
        int regionY = MathHelper.floor_double(this.boundingBox.minY);
        int regionZ = MathHelper.floor_double(this.posZ);
        boolean firstRegion = this.lastAggroObstacleRegionX == Integer.MIN_VALUE;
        boolean enteredNewRegion = firstRegion
                || regionX != this.lastAggroObstacleRegionX
                || regionY != this.lastAggroObstacleRegionY
                || regionZ != this.lastAggroObstacleRegionZ;

        if (enteredNewRegion) {
            this.lastAggroObstacleRegionX = regionX;
            this.lastAggroObstacleRegionY = regionY;
            this.lastAggroObstacleRegionZ = regionZ;
        }

        EntityLivingBase target = this.getAttackTarget();
        if (target != null && !target.isEntityAlive()) {
            target = null;
        }

        /*
         * Trigger conditions are deliberately state-driven. A new sweep begins
         * only on a new occupied block region, a real horizontal collision, a
         * missing combat path, measured lack of progress, or an explicit
         * pre-path request from the chase AI.
         */
        double movedX = this.posX - this.prevPosX;
        double movedZ = this.posZ - this.prevPosZ;
        boolean moving = movedX * movedX + movedZ * movedZ > 0.0025D
                || Math.abs(this.moveForward) > 0.05F
                || !this.getNavigator().noPath();
        boolean collidedWhileMoving =
                moving && this.isCollidedHorizontally;
        boolean targetNeedsPath = target != null
                && this.shouldBreakForwardTrees()
                && this.getDistanceSqToEntity(target)
                > TUSK_ATTACK_RANGE * TUSK_ATTACK_RANGE;
        boolean targetHasNoPath =
                targetNeedsPath && this.getNavigator().noPath();
        boolean failedToProgress =
                this.updateAggroObstacleProgressState(
                        target,
                        targetNeedsPath
                );
        boolean regionTrigger =
                enteredNewRegion && (firstRegion || moving);

        if (this.aggroObstacleSliceLimit <= 0) {
            boolean shouldStartSweep = force
                    || regionTrigger
                    || collidedWhileMoving
                    || targetHasNoPath
                    || failedToProgress;
            if (!shouldStartSweep) {
                return;
            }

            /*
             * A completed obstructed sweep gets a short retry; a clear sweep
             * gets a longer one. Entering a new region or an explicit chase
             * request may start immediately, while stationary retries honor
             * the cooldown.
             */
            if (!force
                    && !regionTrigger
                    && this.ticksExisted
                    < this.nextAggroObstacleSweepTick) {
                return;
            }

            this.aggroObstacleSliceIndex = 0;
            this.aggroObstacleSliceLimit =
                    this.shouldBreakForwardTrees()
                            ? AGGRO_OBSTACLE_SLICE_COUNT
                            : 1;
            this.aggroObstacleSweepBrokeBlocks = false;
        } else if (!this.shouldBreakForwardTrees()
                && this.aggroObstacleSliceIndex > 0) {
            this.finishAggroObstacleSweep();
            return;
        }

        /*
         * Work is staggered by entity ID. Each pass examines one spatial slice
         * and never more than AGGRO_OBSTACLE_BLOCK_CHECK_BUDGET blocks.
         */
        int entityStagger =
                (this.getEntityId() & Integer.MAX_VALUE)
                        % AGGRO_OBSTACLE_CLEAR_INTERVAL;
        if ((this.ticksExisted % AGGRO_OBSTACLE_CLEAR_INTERVAL
                + entityStagger)
                % AGGRO_OBSTACLE_CLEAR_INTERVAL != 0
                || this.lastAggroObstacleClearTick
                == this.ticksExisted) {
            return;
        }

        AxisAlignedBB obstacleSlice =
                this.createAggroObstacleSlice(
                        this.aggroObstacleSliceIndex,
                        target
                );
        if (obstacleSlice == null) {
            this.finishAggroObstacleSweep();
            return;
        }

        this.lastAggroObstacleClearTick = this.ticksExisted;
        if (MumakilPerformanceTracker.isEnabled()) {
            MumakilPerformanceTracker.recordTreePass(this);
        }

        int broken = this.clearAggroObstaclesInBox(
                obstacleSlice,
                AGGRO_OBSTACLE_BLOCK_CHECK_BUDGET,
                MAX_OBSTACLES_PER_PASS
        );
        this.aggroObstacleSweepBrokeBlocks |= broken > 0;
        ++this.aggroObstacleSliceIndex;

        if (this.aggroObstacleSliceIndex
                >= this.aggroObstacleSliceLimit) {
            this.finishAggroObstacleSweep();
        }
    }

    private boolean updateAggroObstacleProgressState(
            EntityLivingBase target,
            boolean targetNeedsPath
    ) {
        if (target == null || !targetNeedsPath) {
            this.hasAggroObstacleProgressSample = false;
            this.nextAggroObstacleProgressCheckTick = 0;
            return false;
        }

        if (!this.hasAggroObstacleProgressSample) {
            this.lastAggroObstacleProgressX = this.posX;
            this.lastAggroObstacleProgressZ = this.posZ;
            this.nextAggroObstacleProgressCheckTick =
                    this.ticksExisted
                            + AGGRO_OBSTACLE_PROGRESS_CHECK_TICKS;
            this.hasAggroObstacleProgressSample = true;
            return false;
        }

        if (this.ticksExisted
                < this.nextAggroObstacleProgressCheckTick) {
            return false;
        }

        double progressX =
                this.posX - this.lastAggroObstacleProgressX;
        double progressZ =
                this.posZ - this.lastAggroObstacleProgressZ;
        boolean failed = progressX * progressX
                + progressZ * progressZ
                < AGGRO_OBSTACLE_PROGRESS_DISTANCE_SQ;

        this.lastAggroObstacleProgressX = this.posX;
        this.lastAggroObstacleProgressZ = this.posZ;
        this.nextAggroObstacleProgressCheckTick =
                this.ticksExisted
                        + AGGRO_OBSTACLE_PROGRESS_CHECK_TICKS;
        return failed;
    }

    private void finishAggroObstacleSweep() {
        this.aggroObstacleSliceIndex = 0;
        this.aggroObstacleSliceLimit = 0;
        this.nextAggroObstacleSweepTick =
                this.ticksExisted
                        + (this.aggroObstacleSweepBrokeBlocks
                        ? AGGRO_OBSTACLE_BROKEN_RETRY_TICKS
                        : AGGRO_OBSTACLE_SWEEP_RETRY_TICKS);
        this.aggroObstacleSweepBrokeBlocks = false;
    }

    private AxisAlignedBB createAggroObstacleSlice(
            int sliceIndex,
            EntityLivingBase target
    ) {
        /*
         * Slice 0 immediately clears footing. The remaining twelve passes are
         * four vertical slices each for the body, near corridor, and far
         * corridor. This keeps torso/head clearance responsive without ever
         * scanning the complete multi-thousand-block corridor in one tick.
         */
        if (sliceIndex == 0) {
            return AxisAlignedBB.getBoundingBox(
                    this.posX - 3.5D,
                    this.boundingBox.minY - 1.5D,
                    this.posZ - 3.5D,
                    this.posX + 3.5D,
                    this.boundingBox.minY + 2.0D,
                    this.posZ + 3.5D
            );
        }

        if (sliceIndex < 1
                || sliceIndex >= AGGRO_OBSTACLE_SLICE_COUNT) {
            return null;
        }

        int group = (sliceIndex - 1) / 4;
        int orderedSlice =
                AGGRO_OBSTACLE_VERTICAL_SLICE_ORDER[
                        (sliceIndex - 1) % 4
                        ];
        double centerX = this.posX;
        double centerZ = this.posZ;
        double halfWidth = 3.5D;
        double minimumY = this.boundingBox.minY + 0.25D;
        double maximumY = this.boundingBox.maxY + 1.25D;

        if (group > 0) {
            double lookX;
            double lookZ;
            if (target != null && target.isEntityAlive()) {
                lookX = target.posX - this.posX;
                lookZ = target.posZ - this.posZ;
            } else {
                Vec3 look = this.getLookVec();
                lookX = look.xCoord;
                lookZ = look.zCoord;
            }

            double lookLength = Math.sqrt(
                    lookX * lookX + lookZ * lookZ
            );
            if (lookLength > 1.0E-4D) {
                lookX /= lookLength;
                lookZ /= lookLength;
            } else {
                lookX = -MathHelper.sin(
                        this.rotationYaw
                                * (float)Math.PI / 180.0F
                );
                lookZ = MathHelper.cos(
                        this.rotationYaw
                                * (float)Math.PI / 180.0F
                );
            }

            double distance = group == 1 ? 4.0D : 8.0D;
            centerX += lookX * distance;
            centerZ += lookZ * distance;
            halfWidth = group == 1 ? 3.0D : 3.25D;
            maximumY = this.boundingBox.maxY + 1.5D;
        }

        double sliceHeight =
                (maximumY - minimumY) / 4.0D;
        double sliceMinY =
                minimumY + sliceHeight * orderedSlice;
        double sliceMaxY =
                orderedSlice == 3
                        ? maximumY
                        : minimumY
                        + sliceHeight * (orderedSlice + 1)
                        - 0.001D;

        return AxisAlignedBB.getBoundingBox(
                centerX - halfWidth,
                sliceMinY,
                centerZ - halfWidth,
                centerX + halfWidth,
                sliceMaxY,
                centerZ + halfWidth
        );
    }

    private boolean shouldBreakForwardTrees() {
        if (!MumakilConfig.mumakBreaksTrees) {
            return false;
        }
        if (this.riddenByEntity instanceof EntityPlayer) {
            return false;
        }

        EntityLivingBase target = this.getAttackTarget();
        if (target == null || !target.isEntityAlive()) {
            return false;
        }

        return this.isWildAdultMumakil()
                || this.isHiredWarMumakil()
                || this.isMountEnraged();
    }
    private int clearAggroObstaclesInBox(
            AxisAlignedBB obstacleBox,
            int maximumChecks,
            int maximumBroken
    ) {
        int minX = MathHelper.floor_double(obstacleBox.minX);
        int maxX = MathHelper.floor_double(obstacleBox.maxX);
        int minY = MathHelper.floor_double(obstacleBox.minY);
        int maxY = MathHelper.floor_double(obstacleBox.maxY);
        int minZ = MathHelper.floor_double(obstacleBox.minZ);
        int maxZ = MathHelper.floor_double(obstacleBox.maxZ);
        int broken = 0;
        int checked = 0;

        for (int x = minX; x <= maxX; ++x) {
            for (int y = minY; y <= maxY; ++y) {
                for (int z = minZ; z <= maxZ; ++z) {
                    if (checked >= maximumChecks
                            || broken >= maximumBroken) {
                        if (MumakilPerformanceTracker.isEnabled()) {
                            MumakilPerformanceTracker.recordTreeBlocksChecked(this, checked);
                        }
                        return broken;
                    }

                    ++checked;
                    if (!this.worldObj.blockExists(x, y, z)) {
                        continue;
                    }
                    Block block = this.worldObj.getBlock(x, y, z);
                    if (this.canBreakAggroObstacle(block, x, y, z)) {
                        this.breakAggroObstacleBlock(block, x, y, z);
                        ++broken;
                        if (MumakilPerformanceTracker.isEnabled()) {
                            MumakilPerformanceTracker.recordTreeBlocksDestroyed(this, 1);
                        }
                    }
                }
            }
        }

        if (MumakilPerformanceTracker.isEnabled()) {
            MumakilPerformanceTracker.recordTreeBlocksChecked(this, checked);
        }

        return broken;
    }

    private void breakAggroObstacleBlock(Block block, int x, int y, int z) {
        int metadata = this.worldObj.getBlockMetadata(x, y, z);

        /*
         * Drop normal block drops first.
         * Logs drop logs.
         * Leaves can drop saplings/apples according to their normal drop logic.
         */
        block.dropBlockAsItem(this.worldObj, x, y, z, metadata, 0);

        /*
         * Block break particles/sound.
         */
        this.worldObj.playAuxSFX(
                2001,
                x,
                y,
                z,
                Block.getIdFromBlock(block) + (metadata << 12)
        );

        this.worldObj.setBlockToAir(x, y, z);
    }

    private boolean canBreakAggroObstacle(Block block, int x, int y, int z) {
        // QUARTER_SPEED_RIDDEN_PANIC_AND_STRICT_TREE_BLOCKS_V2
        // Strictly tree blocks only: no grass, bushes, crops, or vines.
        return block.isLeaves(this.worldObj, x, y, z)
                || block.isWood(this.worldObj, x, y, z);
    }


    // ---------------------------------------------------------------------
    // Idle yaw stabilization and fallback-facing helpers
    // ---------------------------------------------------------------------

    private void faceAIMovementDirection() {
        if (this.riddenByEntity instanceof EntityPlayer
                || this.shouldPreserveAuthoredCombatFacing()) {
            return;
        }

        Vec3 directionPoint = this.getAIMovementDirectionPoint();
        if (directionPoint == null) {
            return;
        }

        double deltaX = directionPoint.xCoord - this.posX;
        double deltaZ = directionPoint.zCoord - this.posZ;
        if (deltaX * deltaX + deltaZ * deltaZ
                < AI_MOVEMENT_PATH_DIRECTION_MIN_SQ) {
            return;
        }

        float desiredYaw = (float)(
                Math.atan2(deltaZ, deltaX)
                        * 180.0D / Math.PI
        ) - 90.0F;
        this.renderYawOffset = this.clampYawStep(
                this.renderYawOffset,
                desiredYaw,
                AI_MOVEMENT_MAX_TURN_RATE
        );
        this.rotationYaw = this.renderYawOffset;

        float headFromBody = MathHelper.wrapAngleTo180_float(
                this.rotationYawHead - this.renderYawOffset
        );
        this.rotationYawHead = this.renderYawOffset
                + MathHelper.clamp_float(
                headFromBody,
                -IDLE_HEAD_YAW_LIMIT,
                IDLE_HEAD_YAW_LIMIT
        );
    }

    private Vec3 getAIMovementDirectionPoint() {
        PathEntity path = this.getNavigator().getPath();
        if (path != null && !path.isFinished()) {
            int index = path.getCurrentPathIndex();
            int length = path.getCurrentPathLength();
            if (index >= 0 && index < length) {
                Vec3 pathPoint = path.getVectorFromIndex(
                        this,
                        index
                );
                double pathDeltaX = pathPoint.xCoord - this.posX;
                double pathDeltaZ = pathPoint.zCoord - this.posZ;
                if (pathDeltaX * pathDeltaX
                        + pathDeltaZ * pathDeltaZ
                        < AI_MOVEMENT_PATH_DIRECTION_MIN_SQ
                        && index + 1 < length) {
                    pathPoint = path.getVectorFromIndex(
                            this,
                            index + 1
                    );
                }

                pathDeltaX = pathPoint.xCoord - this.posX;
                pathDeltaZ = pathPoint.zCoord - this.posZ;
                if (pathDeltaX * pathDeltaX
                        + pathDeltaZ * pathDeltaZ
                        >= AI_MOVEMENT_PATH_DIRECTION_MIN_SQ) {
                    return pathPoint;
                }
            }
        }

        double horizontalMotionSq =
                this.motionX * this.motionX
                        + this.motionZ * this.motionZ;
        if (horizontalMotionSq
                < AI_MOVEMENT_MOTION_THRESHOLD_SQ) {
            return null;
        }

        return Vec3.createVectorHelper(
                this.posX + this.motionX * 8.0D,
                this.posY,
                this.posZ + this.motionZ * 8.0D
        );
    }

    private boolean shouldPreserveAuthoredCombatFacing() {
        if ((this.mumakilStrikeAnimationTicks > 0
                && (!this.isAutonomousCombatPassActive()
                || this.mumakilStrikeAnimationTicks
                > MUMAKIL_STRIKE_ANIMATION_TICKS
                - AUTONOMOUS_STRIKE_FACING_LOCK_TICKS))
                || this.isMumakilTrumpetAnimationActive()
                || this.isTerritorialWarningActive()) {
            return true;
        }

        EntityLivingBase target = this.getAttackTarget();
        return target != null
                && !this.isAutonomousCombatPassActive()
                && target.isEntityAlive()
                && this.getDistanceSqToEntity(target)
                <= AI_CLOSE_COMBAT_FACING_RANGE_SQ
                && (this.canEntityBeSeen(target)
                || this.getDistanceSqToEntity(target)
                <= TUSK_ATTACK_RANGE * TUSK_ATTACK_RANGE);
    }

    private void stabilizeIdleYaw() {
        if (!this.isStationaryIdleForYawLock()) {
            this.rememberStableIdleYaw();
            return;
        }

        if (!this.hasStableIdleYaw) {
            this.rememberStableIdleYaw();
            return;
        }

        boolean corrected = false;
        float bodyDelta = MathHelper.wrapAngleTo180_float(this.renderYawOffset - this.lastStableIdleYaw);
        if (Math.abs(bodyDelta) > IDLE_YAW_SNAP_THRESHOLD) {
            this.renderYawOffset = this.clampYawStep(this.lastStableIdleYaw, this.renderYawOffset, IDLE_YAW_MAX_STEP);
            corrected = true;
        }

        float entityDelta = MathHelper.wrapAngleTo180_float(this.rotationYaw - this.lastStableIdleYaw);
        if (Math.abs(entityDelta) > IDLE_YAW_SNAP_THRESHOLD) {
            this.rotationYaw = this.clampYawStep(this.lastStableIdleYaw, this.rotationYaw, IDLE_YAW_MAX_STEP);
            corrected = true;
        }

        float headDelta = MathHelper.wrapAngleTo180_float(this.rotationYawHead - this.lastStableIdleHeadYaw);
        float headFromBody = MathHelper.wrapAngleTo180_float(this.rotationYawHead - this.renderYawOffset);
        if (Math.abs(headDelta) > IDLE_YAW_SNAP_THRESHOLD && Math.abs(headFromBody) > IDLE_HEAD_YAW_LIMIT) {
            this.rotationYawHead = this.renderYawOffset + MathHelper.clamp_float(headFromBody, -IDLE_HEAD_YAW_LIMIT, IDLE_HEAD_YAW_LIMIT);
            corrected = true;
        }

        if (corrected) {
            this.prevRotationYaw = this.rotationYaw;
            this.prevRenderYawOffset = this.renderYawOffset;
            this.prevRotationYawHead = this.rotationYawHead;
        }

        this.rememberStableIdleYaw();
    }

    private boolean isStationaryIdleForYawLock() {
        if (this.riddenByEntity != null
                || this.getAttackTarget() != null
                || this.isMountEnraged()
                || this.isTerritorialWarningActive()
                || this.isWildHerdRegroupActive()
                || this.isSprinting()
                || !this.onGround
                || Math.abs(this.moveForward) > 0.01F
                || Math.abs(this.moveStrafing) > 0.01F) {
            return false;
        }

        double horizontalMotionSq = this.motionX * this.motionX + this.motionZ * this.motionZ;
        return horizontalMotionSq <= IDLE_YAW_MOTION_THRESHOLD_SQ && this.getNavigator().noPath();
    }

    private void rememberStableIdleYaw() {
        this.lastStableIdleYaw = this.renderYawOffset;
        this.lastStableIdleHeadYaw = this.rotationYawHead;
        this.hasStableIdleYaw = true;
    }

    private float clampYawStep(float stableYaw, float candidateYaw, float maximumStep) {
        float delta = MathHelper.wrapAngleTo180_float(candidateYaw - stableYaw);
        return stableYaw + MathHelper.clamp_float(delta, -maximumStep, maximumStep);
    }

    private void faceWildMovePoint(double x, double z) {
        double deltaX = x - this.posX;
        double deltaZ = z - this.posZ;
        if (deltaX * deltaX + deltaZ * deltaZ < 1.0E-4D) {
            return;
        }

        float desiredYaw = (float)(Math.atan2(deltaZ, deltaX) * 180.0D / Math.PI) - 90.0F;
        this.rotationYaw = this.clampYawStep(this.rotationYaw, desiredYaw, WILD_FALLBACK_TURN_STEP);
        this.renderYawOffset = this.clampYawStep(this.renderYawOffset, this.rotationYaw, WILD_FALLBACK_TURN_STEP);
        this.rotationYawHead = this.renderYawOffset;
    }


    // ---------------------------------------------------------------------
    // Anger wave, sounds, strike animation, and drops
    // ---------------------------------------------------------------------

    private void updateAngerWave() {
        if (!this.isWildAdultMumakil()) {
            this.angerWaveActiveTicks = 0;
            if (this.angerWaveCooldownTicks <= 0) {
                this.resetAngerWaveCooldown();
            }
            return;
        }

        if (this.angerWaveActiveTicks > 0) {
            --this.angerWaveActiveTicks;
            if (this.angerWaveActiveTicks <= 0) {
                this.resetAngerWaveCooldown();
            }
            return;
        }

        if (this.angerWaveCooldownTicks > 0) {
            --this.angerWaveCooldownTicks;
            return;
        }

        this.angerWaveActiveTicks = ANGER_WAVE_MIN_DURATION + this.rand.nextInt(ANGER_WAVE_RANDOM_DURATION);
        this.playMumakilAngrySound();
    }

    private void resetAngerWaveCooldown() {
        this.angerWaveCooldownTicks = ANGER_WAVE_MIN_COOLDOWN + this.rand.nextInt(ANGER_WAVE_RANDOM_COOLDOWN);
    }

    private boolean isWildAngerWaveActive() {
        return this.isWildAdultMumakil() && this.angerWaveActiveTicks > 0;
    }

    private boolean shouldPlayMumakilAngrySoundThisTrigger() {
        ++this.mumakilAngrySoundTriggerCounter;
        return this.mumakilAngrySoundTriggerCounter % 10 == 0;
    }

    private void updateMumakilAmbientAnimationSounds() {
        this.updateMumakilTrumpetSequence();
        this.tryPlayScheduledAnimationSound(
                AMBIENT_EAR_FLAP_INTERVAL_TICKS,
                AMBIENT_EAR_FLAP_CHANCE_MODULO,
                71,
                "lotrmoremobs:mumakil.ears",
                1.85F,
                0.78F,
                0.08F
        );
    }

    private void startMumakilTrumpetSequenceNow() {
        if (this.worldObj == null
                || this.worldObj.isRemote
                || !this.isEntityAlive()
                || this.getMumakilTrumpetAnimationTicks() > 0) {
            return;
        }
        this.setMumakilTrumpetAnimationTicks(
                MUMAKIL_TRUMPET_ANIMATION_TICKS
        );
        this.mumakilTrumpetSoundPlayed = false;
    }

    private void updateMumakilTrumpetSequence() {
        if (!this.isEntityAlive() || this.isDead) {
            this.setMumakilTrumpetAnimationTicks(0);
            this.mumakilTrumpetSoundPlayed = true;
            return;
        }

        int remaining = this.getMumakilTrumpetAnimationTicks();
        if (remaining <= 0) {
            if (!this.shouldStartScheduledAnimation(
                    AMBIENT_TRUMPET_INTERVAL_TICKS,
                    AMBIENT_TRUMPET_CHANCE_MODULO,
                    37
            )) {
                return;
            }

            remaining = MUMAKIL_TRUMPET_ANIMATION_TICKS;
            this.setMumakilTrumpetAnimationTicks(remaining);
            this.mumakilTrumpetSoundPlayed = false;
        }

        int elapsed =
                MUMAKIL_TRUMPET_ANIMATION_TICKS - remaining;
        if (!this.mumakilTrumpetSoundPlayed
                && elapsed
                >= MUMAKIL_TRUMPET_SOUND_DELAY_TICKS) {
            boolean baby = this.isBabyMumakil();
            float basePitch = baby ? 1.08F : 0.92F;
            float pitchRange = baby ? 0.10F : 0.12F;
            float pitch = basePitch
                    + (this.rand.nextFloat()
                    - this.rand.nextFloat())
                    * pitchRange;
            this.worldObj.playSoundAtEntity(
                    this,
                    baby
                            ? "lotrmoremobs:mumakil.calftrumpet"
                            : "lotrmoremobs:mumakil.trumpet",
                    baby ? 1.4F : 2.2F,
                    pitch
            );
            this.mumakilTrumpetSoundPlayed = true;
        }

        --remaining;
        this.setMumakilTrumpetAnimationTicks(remaining);
        if (remaining <= 0) {
            this.mumakilTrumpetSoundPlayed = false;
        }
    }

    private boolean shouldStartScheduledAnimation(
            int intervalTicks,
            int chanceModulo,
            int seed
    ) {
        if (intervalTicks <= 0
                || chanceModulo <= 0
                || this.ticksExisted <= 0
                || this.ticksExisted % intervalTicks != 0) {
            return false;
        }

        int cycle = this.ticksExisted / intervalTicks;
        return positiveModulo(
                hashAnimationEvent(
                        this.getEntityId(),
                        cycle,
                        seed
                ),
                chanceModulo
        ) == 0;
    }

    private void tryPlayScheduledAnimationSound(int intervalTicks, int chanceModulo, int seed,
                                                String soundName, float volume, float basePitch,
                                                float pitchRange) {
        if (!this.shouldStartScheduledAnimation(
                intervalTicks,
                chanceModulo,
                seed
        )) {
            return;
        }

        float pitch = basePitch + (this.rand.nextFloat() - this.rand.nextFloat()) * pitchRange;
        this.worldObj.playSoundAtEntity(this, soundName, volume, pitch);
    }

    private static int hashAnimationEvent(int entityId, int cycle, int seed) {
        int value = entityId;
        value = value * 31 + cycle;
        value = value * 31 + seed;
        value ^= value << 13;
        value ^= value >> 17;
        value ^= value << 5;
        return value;
    }

    private static int positiveModulo(int value, int modulo) {
        int result = value % modulo;
        return result < 0 ? result + modulo : result;
    }

    private void playMumakilNormalHitSound() {
        this.worldObj.playSoundAtEntity(
                this,
                "game.neutral.hurt",
                0.9F,
                0.62F + this.rand.nextFloat() * 0.10F
        );
    }

    private void playMumakilAngrySound() {
        if (this.isBabyMumakil() || !this.shouldPlayMumakilAngrySoundThisTrigger()) {
            return;
        }

        this.worldObj.playSoundAtEntity(
                this,
                "lotrmoremobs:mumakil.angry",
                1.25F,
                0.62F + this.rand.nextFloat() * 0.10F
        );
    }

    private void playMumakilHitSound() {
        this.worldObj.playSoundAtEntity(
                this,
                "lotrmoremobs:mumakil.trample",
                1.2F,
                0.75F + this.rand.nextFloat() * 0.15F
        );
    }

    @Override
    public void playSound(String soundName, float volume, float pitch) {
        if ("mob.horse.jump".equals(soundName)
                || "mob.horse.land".equals(soundName)) {
            return;
        }

        super.playSound(soundName, volume, pitch);
    }

    @Override
    protected void func_145780_a(int x, int y, int z, Block block) {
        // Mumakil footsteps are synchronized to the visible locomotion cycle
        // in updateMumakilFootfallPhase().
    }

    protected void dropFewItems(boolean flag, int lootingLevel) {
        int boundedLooting = Math.max(0, lootingLevel);
        int shanks;
        int tusks;

        if (this.isBabyMumakil()) {
            shanks = Math.min(
                    4,
                    2 + this.rand.nextInt(2)
                            + this.rand.nextInt(boundedLooting + 1)
            );
            if (this.captiveBred) {
                // Captive-bred calves are renewable mounts, not an efficient
                // replacement for hunting wild Mumakil for trophy tusks.
                tusks = Math.min(
                        2,
                        this.rand.nextInt(2)
                                + this.rand.nextInt(boundedLooting + 1)
                );
            } else {
                tusks = Math.min(
                        6,
                        1 + this.rand.nextInt(2)
                                + this.rand.nextInt(boundedLooting + 1)
                );
            }
        } else {
            shanks = 4;
            if (this.captiveBred) {
                tusks = Math.min(
                        3,
                        1 + this.rand.nextInt(2)
                                + this.rand.nextInt(boundedLooting + 1)
                );
            } else {
                tusks = Math.min(
                        6,
                        4 + this.rand.nextInt(2)
                                + this.rand.nextInt(boundedLooting + 1)
                );
            }
        }

        for (int j = 0; j < shanks; ++j) {
            this.dropItem(Main.mumakilShank, 1);
        }

        for (int j = 0; j < tusks; ++j) {
            this.dropItem(Main.mumakilTusk, 1);
        }
    }

    @Override
    public void dropChestItems() {
        if (this.getFormationOrigin() != MumakilFormationOrigin.NONE
                || this.isHiredWarMumakil()) {
            IInventory inventory = this.getMumakilMountInventory();
            if (inventory != null) {
                for (int slot = 0; slot < inventory.getSizeInventory(); ++slot) {
                    inventory.setInventorySlotContents(slot, null);
                }
                inventory.markDirty();
            }
            return;
        }

        super.dropChestItems();
    }

    protected float getSoundPitch() {
        return 0.62F + this.rand.nextFloat() * 0.10F;
    }

    protected String getLivingSound() {
        return null;
    }

    protected String getHurtSound() {
        return !this.isBabyMumakil() && this.shouldPlayMumakilAngrySoundThisTrigger()
                ? "lotrmoremobs:mumakil.angry"
                : null;
    }

    protected String getDeathSound() {
        return "lotrmoremobs:mumakil.death";
    }

    protected String getAngrySoundName() {
        return !this.isBabyMumakil() && this.shouldPlayMumakilAngrySoundThisTrigger()
                ? "lotrmoremobs:mumakil.angry"
                : null;
    }

    private void startMumakilStrikeAnimation() {
        this.mumakilStrikeAnimationLeft = this.rand.nextBoolean();
        this.mumakilStrikeAnimationTicks = MUMAKIL_STRIKE_ANIMATION_TICKS;
        this.prevMumakilStrikeAnimationTicks = this.mumakilStrikeAnimationTicks;

        if (DEBUG_COMBAT_LOGS) {
            System.out.println("[LOTRMoreMobs] Starting Mumakil strike animation side="
                    + (this.mumakilStrikeAnimationLeft ? "left" : "right")
                    + " worldRemote=" + this.worldObj.isRemote
                    + " entityId=" + this.getEntityId());
        }

        this.swingItem();

        this.worldObj.playSoundAtEntity(
                this,
                "lotrmoremobs:mumakil.strike",
                1.2F,
                0.85F + this.rand.nextFloat() * 0.2F
        );

        if (!this.worldObj.isRemote) {
            this.worldObj.setEntityState(
                    this,
                    this.mumakilStrikeAnimationLeft ? MUMAKIL_STRIKE_LEFT_STATUS : MUMAKIL_STRIKE_RIGHT_STATUS
            );
        }
    }

    public void handleHealthUpdate(byte status) {
        if (status == MUMAKIL_BABY_PANIC_START_STATUS
                || status == MUMAKIL_BABY_PANIC_STOP_STATUS) {
            this.babyPanicAnimationActive =
                    status == MUMAKIL_BABY_PANIC_START_STATUS;
            return;
        }

        if (status == MUMAKIL_STRIKE_LEFT_STATUS || status == MUMAKIL_STRIKE_RIGHT_STATUS) {
            this.mumakilStrikeAnimationLeft = status == MUMAKIL_STRIKE_LEFT_STATUS;
            this.mumakilStrikeAnimationTicks = MUMAKIL_STRIKE_ANIMATION_TICKS;
            this.prevMumakilStrikeAnimationTicks = this.mumakilStrikeAnimationTicks;

            if (DEBUG_COMBAT_LOGS) {
                System.out.println("[LOTRMoreMobs] Client received Mumakil strike animation side="
                        + (this.mumakilStrikeAnimationLeft ? "left" : "right")
                        + " entityId=" + this.getEntityId());
            }

            return;
        }

        super.handleHealthUpdate(status);
    }

    public float getMumakilStrikeAnimationProgress(float partialTicks) {
        if (this.mumakilStrikeAnimationTicks <= 0 && this.prevMumakilStrikeAnimationTicks <= 0) {
            return 0.0F;
        }

        float remaining = this.prevMumakilStrikeAnimationTicks
                + (this.mumakilStrikeAnimationTicks - this.prevMumakilStrikeAnimationTicks) * partialTicks;

        float progress = 1.0F - remaining / (float)MUMAKIL_STRIKE_ANIMATION_TICKS;

        if (progress < 0.0F) {
            return 0.0F;
        }

        if (progress > 1.0F) {
            return 1.0F;
        }

        return progress;
    }

    public boolean isMumakilStrikeAnimationLeft() {
        return this.mumakilStrikeAnimationLeft;
    }

}
