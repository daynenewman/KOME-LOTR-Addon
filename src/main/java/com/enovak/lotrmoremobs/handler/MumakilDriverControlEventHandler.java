package com.enovak.lotrmoremobs.handler;

import com.enovak.lotrmoremobs.entity.animal.LOTREntityMumakil;
import com.enovak.lotrmoremobs.entity.npc.LOTREntityMumakilHowdahArcher;
import com.enovak.lotrmoremobs.util.MumakilPerformanceTracker;
import com.enovak.lotrmoremobs.util.MumakilServerPerformanceDiagnostics;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.gameevent.TickEvent;
import lotr.common.LOTRLevelData;
import lotr.common.LOTRPlayerData;
import lotr.common.LOTRMod;
import lotr.common.entity.animal.LOTRAmbientCreature;
import lotr.common.entity.npc.LOTREntityNPC;
import lotr.common.entity.npc.LOTREntitySouthronChampion;
import lotr.common.fac.LOTRFaction;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.passive.EntityHorse;
import net.minecraft.entity.passive.EntityTameable;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * LOTRMoreMobs Mumakil driver-control handler.
 *
 * Purpose:
 * - Controls hired-war Mumakil while they have a valid Near Harad/Southron driver.
 * - Keeps hired-war Mumakil fighting as Near Harad combatants if that driver dies.
 * - Keeps wild Mumakil behavior untouched.
 * - Keeps renderer/howdah/archer/equipment systems untouched.
 * - Prevents NPC-driven Mumakil from fixating forever on unreachable or elevated tower targets.
 *
 * Patch focus:
 * - Per-Mumakil NBT target state.
 * - Temporary target rejection.
 * - Elevated fortress/tower target skip for melee driver targeting.
 * - Throttled target acquisition scans.
 */
public class MumakilDriverControlEventHandler {

    private static final String NBT_NEXT_TARGET_SCAN_TICK = "lotrmoremobs_driverNextTargetScanTick";

    private static final double TARGET_SCAN_RANGE = 16.0D;
    private static final double TARGET_SCAN_VERTICAL_RANGE = 8.0D;
    /*
     * Match vanilla/LOTR target retention more closely: use the Mumak's
     * follow-range attribute (32 blocks for adults) and remember a target
     * through only a short loss-of-sight window instead of a fixed 48-block
     * formation leash.
     */
    private static final int DRIVER_TARGET_UNSEEN_MEMORY_TICKS = 60;

    private static final double APPROACH_STOP_RANGE = 7.0D;

    private static final int DRIVER_TARGET_PROGRESS_CHECK_INTERVAL = 20;
    private static final int DRIVER_TARGET_STUCK_TIMEOUT = 100; // about 5 seconds
    private static final int DRIVER_TARGET_REJECT_TICKS = 200; // about 10 seconds
    private static final double DRIVER_PROGRESS_MOVE_THRESHOLD_SQ = 1.0D;
    private static final double DRIVER_TARGET_REACHABLE_EXTRA_RANGE = 4.0D;

    /*
     * Tower/fortress protection:
     * A driven Mumakil is a melee siege animal, not a wall-climber.
     * Elevated archers should be handled by howdah archers, not by Mumakil pathing.
     */
    private static final double DRIVER_TARGET_MAX_Y_ABOVE_MUMAKIL = 6.0D;
    private static final int DRIVER_TARGET_ELEVATED_REJECT_TICKS = 200;

    /*
     * Do not scan crowded battlefields every tick.
     * This only throttles new target acquisition, not ordinary mount movement/control.
     */
    private static final int DRIVER_TARGET_SCAN_COOLDOWN = 10;
    private static final int MOUNTED_DRIVER_HORN_CHANCE_DENOMINATOR = 10;
    private static final int MOUNTED_DRIVER_HORN_COOLDOWN_TICKS = 6000;
    private static final int MOUNTED_DRIVER_HORN_WORLD_COOLDOWN_TICKS = 600;
    private static final int MOUNTED_DRIVER_HORN_DISPLAY_TICKS = 40;
    private static final int MOUNTED_DRIVER_TARGET_LOSS_CONFIRM_TICKS = 20;
    private static final String MOUNTED_DRIVER_HORN_SOUND =
            "lotrmoremobs:harad_warhorn";
    private static final String NBT_MOUNTED_DRIVER_NEXT_HORN_TICK =
            "lotrmoremobs_mumakDriverNextHornTick";
    private static final String NBT_DRIVER_ACTIVE =
            "lotrmoremobs_mumakDriverActive";
    private static final String NBT_DRIVER_PARENT_UUID =
            "lotrmoremobs_mumakDriverParentUUID";
    private static final String NBT_DRIVER_EQUIPMENT_STOWED =
            "lotrmoremobs_mumakDriverEquipmentStowed";
    private static final String NBT_DRIVER_STOWED_HELD_ITEM =
            "lotrmoremobs_mumakDriverStowedHeldItem";
    private static final String NBT_DRIVER_STOWED_NPC_ITEMS =
            "lotrmoremobs_mumakDriverStowedNPCItems";
    private static final int DRIVER_ORPHAN_RECOVERY_GRACE_TICKS = 100;

    private static final boolean DEBUG_DRIVER_TARGETS = false;
    private static final boolean DEBUG_DRIVER_HORN = false;
    private static final Map<LOTREntityNPC, Boolean> MOUNTED_DRIVER_COMBAT_GUARDS =
            new WeakHashMap<LOTREntityNPC, Boolean>();
    private static final Map<World, MountedDriverHornWorldState> MOUNTED_DRIVER_HORN_WORLD_STATES =
            new WeakHashMap<World, MountedDriverHornWorldState>();
    private static final Map<LOTREntityMumakil, DriverTargetRuntimeState> DRIVER_TARGET_RUNTIME_STATES =
            new WeakHashMap<LOTREntityMumakil, DriverTargetRuntimeState>();
    private static final Map<LOTREntityNPC, Long> DRIVER_ORPHAN_DEADLINES =
            new WeakHashMap<LOTREntityNPC, Long>();
    private static final Map<EntityPlayer, FastTravelMumakSnapshot>
            FAST_TRAVEL_MUMAK_SNAPSHOTS =
            new WeakHashMap<EntityPlayer, FastTravelMumakSnapshot>();
    private static final Map<EntityPlayer, FastTravelTamedMumakSnapshot>
            FAST_TRAVEL_TAMED_MUMAK_SNAPSHOTS =
            new WeakHashMap<EntityPlayer, FastTravelTamedMumakSnapshot>();

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onMumakilJoinWorldRiderTargetCompatibility(
            EntityJoinWorldEvent event
    ) {
        if (event == null
                || event.world == null
                || event.world.isRemote
                || !(event.entity instanceof LOTREntityMumakil)) {
            return;
        }

        ((LOTREntityMumakil)event.entity)
                .restoreMumakilRiderTargetAICompatibility();
    }

    @SubscribeEvent
    public void onEntityJoinWorld(EntityJoinWorldEvent event) {
        if (event == null
                || event.world == null
                || !(event.entity instanceof LOTREntityNPC)) {
            return;
        }

        LOTREntityNPC possibleDriver = (LOTREntityNPC)event.entity;
        LOTREntityMumakil parent =
                getPhysicallyValidatedDriverMumakil(possibleDriver);
        if (parent != null) {
            activateValidatedDriver(parent, possibleDriver);
        } else if (!event.world.isRemote
                && possibleDriver.getEntityData().getBoolean(
                NBT_DRIVER_EQUIPMENT_STOWED
        )) {
            /*
             * Riding links may be reconstructed after the entity joins a
             * loaded world. Keep the persisted weapon hidden during that
             * recovery window; the ordinary update path validates the parent
             * UUID or restores the snapshot after the grace interval.
             */
            suppressMountedDriverCombatState(possibleDriver);
        }
    }

    @SubscribeEvent
    public void onLivingUpdate(LivingEvent.LivingUpdateEvent event) {
        if (event.entityLiving instanceof LOTREntityNPC) {
            LOTREntityNPC possibleDriver =
                    (LOTREntityNPC)event.entityLiving;
            LOTREntityMumakil parent =
                    getPhysicallyValidatedDriverMumakil(possibleDriver);
            if (parent != null) {
                activateValidatedDriver(parent, possibleDriver);
            } else if (possibleDriver.worldObj != null
                    && !possibleDriver.worldObj.isRemote) {
                recoverOrphanedDriverEquipment(possibleDriver);
            }
        }

        if (!(event.entityLiving instanceof LOTREntityMumakil)) {
            return;
        }

        LOTREntityMumakil mumakil = (LOTREntityMumakil) event.entityLiving;

        if (mumakil.worldObj == null) {
            return;
        }

        LOTREntityNPC driver = getValidNearHaradDriver(mumakil);
        if (mumakil.worldObj.isRemote) {
            if (driver != null) {
                applyMountedDriverPose(mumakil, driver);
                clearMountedDriverHeldItem(driver);
            }
            return;
        }

        if (driver != null
                && !activateValidatedDriver(mumakil, driver)) {
            driver = null;
        }

        boolean trackPerformance =
                MumakilPerformanceTracker.isEnabled();
        long perfStart = trackPerformance
                ? MumakilPerformanceTracker.startTimer()
                : 0L;

        try {
            if (driver != null) {
                markHiredWarIfApplicable(mumakil);
                mumakil.capturePlayerHiredFormationOwner(driver);
            } else if (isImplicitHiredWarMumakil(mumakil)) {
                mumakil.setHiredWarMumakil(true);
            }

            if (!mumakil.isHiredWarMumakil()) {
                if (hasDriverTargetState(mumakil)) {
                    clearDriverTargetState(mumakil);
                }
                if (mumakil.getMountedDriverHornTicks() > 0) {
                    mumakil.setMountedDriverHornTicks(0);
                }
                return;
            }

            if (driver != null) {
                ensureMountedDriverCombatGuard(driver);
            }

            updateDrivenMumakil(mumakil, driver);
            observeMountedDriverTargetAcquisition(
                    mumakil,
                    driver
            );
            updateMountedDriverHorn(mumakil, driver);
        } finally {
            if (trackPerformance) {
                MumakilPerformanceTracker.recordDriverHandler(
                        mumakil,
                        System.nanoTime() - perfStart
                );
            }
        }
    }

    /**
     * LOTR fast travel normally recreates the entity a player is riding, but
     * addon mounts can fall outside that native handoff in some environments.
     * Keep the existing hired-war fallback and add a separate, owner-checked
     * fallback for the player's currently ridden tamed Mumak. The fallback
     * reuses the exact entity/UUID whenever possible and only recreates from
     * the pre-travel NBT snapshot after the origin chunk has been checked for
     * that UUID, preventing duplicate Mumakil.
     */
    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event == null
                || event.player == null
                || event.player.worldObj == null
                || event.player.worldObj.isRemote
                || event.phase != TickEvent.Phase.START) {
            return;
        }

        EntityPlayer player = event.player;
        LOTRPlayerData playerData = LOTRLevelData.getData(player);
        if (playerData.getTargetFTWaypoint() != null) {
            captureHiredWarFastTravelSnapshot(player);
            captureTamedRiddenFastTravelSnapshot(player);
            return;
        }

        finishHiredWarFastTravelFallback(
                player,
                FAST_TRAVEL_MUMAK_SNAPSHOTS.remove(player)
        );
        finishTamedRiddenFastTravelFallback(
                player,
                FAST_TRAVEL_TAMED_MUMAK_SNAPSHOTS.remove(player)
        );
    }

    private static void captureHiredWarFastTravelSnapshot(
            EntityPlayer player
    ) {
        if (FAST_TRAVEL_MUMAK_SNAPSHOTS.containsKey(player)) {
            return;
        }

        LOTREntityNPC driver = findPlayerHiredMumakDriver(player);
        if (driver != null
                && driver.ridingEntity instanceof LOTREntityMumakil) {
            FAST_TRAVEL_MUMAK_SNAPSHOTS.put(
                    player,
                    new FastTravelMumakSnapshot(
                            player,
                            driver,
                            (LOTREntityMumakil)driver.ridingEntity
                    )
            );
        }
    }

    private static void captureTamedRiddenFastTravelSnapshot(
            EntityPlayer player
    ) {
        if (FAST_TRAVEL_TAMED_MUMAK_SNAPSHOTS.containsKey(player)
                || !(player.ridingEntity instanceof LOTREntityMumakil)) {
            return;
        }

        LOTREntityMumakil mumakil =
                (LOTREntityMumakil)player.ridingEntity;
        if (mumakil.isDead
                || !mumakil.isEntityAlive()
                || !mumakil.isTame()
                || !mumakil.isTamedByPlayer(player)
                || mumakil.isHiredWarMumakil()) {
            return;
        }

        FAST_TRAVEL_TAMED_MUMAK_SNAPSHOTS.put(
                player,
                new FastTravelTamedMumakSnapshot(
                        player,
                        mumakil
                )
        );
    }

    private static void finishHiredWarFastTravelFallback(
            EntityPlayer player,
            FastTravelMumakSnapshot snapshot
    ) {
        if (snapshot == null
                || snapshot.playerWorld != player.worldObj
                || snapshot.driver.isDead
                || snapshot.mumakil.isDead
                || snapshot.driver.ridingEntity != snapshot.mumakil
                || snapshot.distanceFromOriginSq(player) < 4096.0D) {
            return;
        }

        double yawRadians = Math.toRadians(player.rotationYaw);
        double spawnX = player.posX - Math.sin(yawRadians) * 8.0D;
        double spawnZ = player.posZ + Math.cos(yawRadians) * 8.0D;
        snapshot.mumakil.setLocationAndAngles(
                spawnX,
                player.posY,
                spawnZ,
                player.rotationYaw,
                snapshot.mumakil.rotationPitch
        );
        snapshot.mumakil.fallDistance = 0.0F;
        snapshot.mumakil.getNavigator().clearPathEntity();
        snapshot.playerWorld.updateEntityWithOptionalForce(
                snapshot.mumakil,
                false
        );
        snapshot.driver.mountEntity(snapshot.mumakil);
        snapshot.driver.setLocationAndAngles(
                spawnX,
                player.posY + snapshot.mumakil.height,
                spawnZ,
                player.rotationYaw,
                snapshot.driver.rotationPitch
        );
        snapshot.playerWorld.updateEntityWithOptionalForce(
                snapshot.driver,
                false
        );
    }

    private static void finishTamedRiddenFastTravelFallback(
            EntityPlayer player,
            FastTravelTamedMumakSnapshot snapshot
    ) {
        if (snapshot == null
                || snapshot.playerWorld != player.worldObj
                || snapshot.distanceFromOriginSq(player) < 4096.0D) {
            return;
        }

        /*
         * If LOTR already recreated/remounted this Mumak successfully, leave
         * the native result alone.
         */
        if (player.ridingEntity instanceof LOTREntityMumakil) {
            LOTREntityMumakil mounted =
                    (LOTREntityMumakil)player.ridingEntity;
            if (snapshot.mumakUuid.equals(mounted.getPersistentID())) {
                return;
            }
        }

        LOTREntityMumakil mumakil = findLoadedMumakByUuid(
                player.worldObj,
                snapshot.mumakUuid
        );

        if (mumakil == null
                && player.worldObj instanceof WorldServer) {
            /*
             * Recover the exact saved origin entity before considering NBT
             * recreation. This is a bounded one-chunk load and prevents an
             * origin copy from being left behind on disk.
             */
            WorldServer worldServer = (WorldServer)player.worldObj;
            worldServer.theChunkProviderServer.provideChunk(
                    snapshot.originChunkX,
                    snapshot.originChunkZ
            );
            mumakil = findLoadedMumakByUuid(
                    player.worldObj,
                    snapshot.mumakUuid
            );
        }

        if (mumakil == null
                && player.worldObj instanceof WorldServer) {
            mumakil = new LOTREntityMumakil(player.worldObj);
            mumakil.readFromNBT(snapshot.entityNbt);
            if (!snapshot.mumakUuid.equals(mumakil.getPersistentID())) {
                return;
            }
            double yawRadians = Math.toRadians(player.rotationYaw);
            mumakil.setLocationAndAngles(
                    player.posX - Math.sin(yawRadians) * 8.0D,
                    player.posY,
                    player.posZ + Math.cos(yawRadians) * 8.0D,
                    player.rotationYaw,
                    mumakil.rotationPitch
            );
            ((WorldServer)player.worldObj).spawnEntityInWorld(mumakil);
        }

        if (mumakil == null
                || mumakil.isDead
                || !mumakil.isEntityAlive()
                || !mumakil.isTame()
                || !mumakil.isTamedByPlayer(player)) {
            return;
        }

        boolean nativeDestinationCopy =
                mumakil.getDistanceSqToEntity(player) <= 1024.0D;
        double yawRadians = Math.toRadians(player.rotationYaw);
        double spawnX = nativeDestinationCopy
                ? mumakil.posX
                : player.posX - Math.sin(yawRadians) * 8.0D;
        double spawnY = nativeDestinationCopy
                ? mumakil.posY
                : player.posY;
        double spawnZ = nativeDestinationCopy
                ? mumakil.posZ
                : player.posZ + Math.cos(yawRadians) * 8.0D;

        mumakil.getNavigator().clearPathEntity();
        mumakil.setAttackTarget(null);
        mumakil.setRevengeTarget(null);
        mumakil.motionX = 0.0D;
        mumakil.motionY = 0.0D;
        mumakil.motionZ = 0.0D;
        mumakil.fallDistance = 0.0F;
        mumakil.setLocationAndAngles(
                spawnX,
                spawnY,
                spawnZ,
                player.rotationYaw,
                mumakil.rotationPitch
        );
        player.worldObj.updateEntityWithOptionalForce(
                mumakil,
                false
        );

        if (player.ridingEntity != null) {
            player.mountEntity(null);
        }
        player.mountEntity(mumakil);
    }

    private static LOTREntityMumakil findLoadedMumakByUuid(
            World world,
            UUID mumakUuid
    ) {
        if (world == null || mumakUuid == null) {
            return null;
        }

        List loaded = world.loadedEntityList;
        for (int i = 0; i < loaded.size(); ++i) {
            Object candidate = loaded.get(i);
            if (candidate instanceof LOTREntityMumakil) {
                LOTREntityMumakil mumakil =
                        (LOTREntityMumakil)candidate;
                if (!mumakil.isDead
                        && mumakil.isEntityAlive()
                        && mumakUuid.equals(
                        mumakil.getPersistentID()
                )) {
                    return mumakil;
                }
            }
        }
        return null;
    }

    private static LOTREntityNPC findPlayerHiredMumakDriver(EntityPlayer player) {
        List loaded = player.worldObj.loadedEntityList;
        for (int i = 0; i < loaded.size(); ++i) {
            if (!(loaded.get(i) instanceof LOTREntityNPC)) {
                continue;
            }
            LOTREntityNPC driver = (LOTREntityNPC)loaded.get(i);
            if (driver.hiredNPCInfo != null
                    && driver.hiredNPCInfo.isActive
                    && driver.hiredNPCInfo.getHiringPlayer() == player
                    && driver.hiredNPCInfo.shouldFollowPlayer()
                    && driver.ridingEntity instanceof LOTREntityMumakil) {
                LOTREntityMumakil mumakil = (LOTREntityMumakil)driver.ridingEntity;
                if (mumakil.isHiredWarMumakil()
                        && mumakil.getFormationOrigin()
                        == com.enovak.lotrmoremobs.entity.animal.MumakilFormationOrigin.PLAYER_HIRED) {
                    return driver;
                }
            }
        }
        return null;
    }

    private static void updateMountedDriverHorn(
            LOTREntityMumakil mumakil,
            LOTREntityNPC driver
    ) {
        if (driver == null) {
            if (mumakil.getMountedDriverHornTicks() > 0) {
                mumakil.setMountedDriverHornTicks(0);
            }
            return;
        }

        clearMountedDriverHeldItem(driver);
        int hornTicks = mumakil.getMountedDriverHornTicks();
        if (hornTicks > 0) {
            int remainingTicks = hornTicks - 1;
            mumakil.setMountedDriverHornTicks(remainingTicks);
            if (remainingTicks == 0) {
                logDriverHorn(
                        mumakil,
                        driver,
                        "horn end"
                );
            }
            return;
        }
    }

    private static void clearMountedDriverHeldItem(LOTREntityNPC driver) {
        if (driver != null && driver.getHeldItem() != null) {
            driver.setCurrentItemOrArmor(0, null);
        }
    }

    private static final class MountedDriverHornWorldState {
        private long quietUntilTick;
    }

    private static final class FastTravelMumakSnapshot {
        private final World playerWorld;
        private final LOTREntityNPC driver;
        private final LOTREntityMumakil mumakil;
        private final double originX;
        private final double originZ;

        private FastTravelMumakSnapshot(
                EntityPlayer player,
                LOTREntityNPC driver,
                LOTREntityMumakil mumakil
        ) {
            this.playerWorld = player.worldObj;
            this.driver = driver;
            this.mumakil = mumakil;
            this.originX = player.posX;
            this.originZ = player.posZ;
        }

        private double distanceFromOriginSq(EntityPlayer player) {
            double dx = player.posX - this.originX;
            double dz = player.posZ - this.originZ;
            return dx * dx + dz * dz;
        }
    }

    private static final class FastTravelTamedMumakSnapshot {
        private final World playerWorld;
        private final UUID mumakUuid;
        private final NBTTagCompound entityNbt;
        private final double originX;
        private final double originZ;
        private final int originChunkX;
        private final int originChunkZ;

        private FastTravelTamedMumakSnapshot(
                EntityPlayer player,
                LOTREntityMumakil mumakil
        ) {
            this.playerWorld = player.worldObj;
            this.mumakUuid = mumakil.getPersistentID();
            this.entityNbt = new NBTTagCompound();
            mumakil.writeToNBT(this.entityNbt);
            this.originX = player.posX;
            this.originZ = player.posZ;
            this.originChunkX = MathHelper.floor_double(mumakil.posX) >> 4;
            this.originChunkZ = MathHelper.floor_double(mumakil.posZ) >> 4;
        }

        private double distanceFromOriginSq(EntityPlayer player) {
            double dx = player.posX - this.originX;
            double dz = player.posZ - this.originZ;
            return dx * dx + dz * dz;
        }
    }

    private static final class DriverTargetRuntimeState {
        private int targetEntityId = -1;
        private int rejectedTargetEntityId = -1;
        private long rejectedUntilTick;
        private int observedTargetEntityId;
        private long targetLostSinceTick;
        private int retentionTargetEntityId = -1;
        private int unseenTargetTicks;
    }

    private static DriverTargetRuntimeState getDriverTargetRuntimeState(
            LOTREntityMumakil mumakil
    ) {
        DriverTargetRuntimeState state =
                DRIVER_TARGET_RUNTIME_STATES.get(mumakil);
        if (state == null) {
            state = new DriverTargetRuntimeState();
            DRIVER_TARGET_RUNTIME_STATES.put(mumakil, state);
        }
        return state;
    }

    /**
     * Establishes the persistent, shooter-specific driver relationship only
     * after the mount/rider link, entity types, world, faction, equipment, and
     * parent state have all been validated in both directions.
     */
    public static boolean activateValidatedDriver(
            LOTREntityMumakil mumakil,
            LOTREntityNPC driver
    ) {
        if (!isPhysicallyValidatedDriverAttachment(mumakil, driver)) {
            return false;
        }

        applyMountedDriverPose(mumakil, driver);
        if (driver.worldObj.isRemote) {
            clearMountedDriverHeldItem(driver);
            return true;
        }

        NBTTagCompound data = driver.getEntityData();
        String parentUuid = mumakil.getPersistentID().toString();
        if (data.getBoolean(NBT_DRIVER_ACTIVE)
                && data.hasKey(NBT_DRIVER_PARENT_UUID)
                && !parentUuid.equals(
                data.getString(NBT_DRIVER_PARENT_UUID)
        )) {
            return false;
        }

        data.setBoolean(NBT_DRIVER_ACTIVE, true);
        data.setString(NBT_DRIVER_PARENT_UUID, parentUuid);
        DRIVER_ORPHAN_DEADLINES.remove(driver);
        stowDriverEquipmentOnce(driver);
        suppressMountedDriverCombatState(driver);
        ensureMountedDriverCombatGuard(driver);
        return true;
    }

    /**
     * Client renderers use the same conservative physical relationship gate.
     * Persistent Forge entity data is server-owned and is therefore checked
     * by activateValidatedDriver before any server-side state is changed.
     */
    public static boolean isFullyAttachedMumakilDriver(Entity entity) {
        if (!(entity instanceof LOTREntityNPC)) {
            return false;
        }
        return getPhysicallyValidatedDriverMumakil(
                (LOTREntityNPC)entity
        ) != null;
    }

    public static void applyMountedDriverPoseIfValid(Entity entity) {
        if (!(entity instanceof LOTREntityNPC)) {
            return;
        }
        LOTREntityNPC driver = (LOTREntityNPC)entity;
        LOTREntityMumakil mumakil =
                getPhysicallyValidatedDriverMumakil(driver);
        if (mumakil != null) {
            applyMountedDriverPose(mumakil, driver);
            clearMountedDriverHeldItem(driver);
        }
    }

    private static void applyMountedDriverPose(
            LOTREntityMumakil mumakil,
            LOTREntityNPC driver
    ) {
        float currentFacing = mumakil.renderYawOffset;
        float previousFacing = mumakil.prevRenderYawOffset;
        driver.rotationYaw = currentFacing;
        driver.prevRotationYaw = previousFacing;
        driver.rotationYawHead = currentFacing;
        driver.prevRotationYawHead = previousFacing;
        driver.renderYawOffset = currentFacing;
        driver.prevRenderYawOffset = previousFacing;

        driver.moveForward = 0.0F;
        driver.moveStrafing = 0.0F;
        driver.prevLimbSwingAmount = 0.0F;
        driver.limbSwingAmount = 0.0F;
        driver.limbSwing = 0.0F;
        driver.prevDistanceWalkedModified = 0.0F;
        driver.distanceWalkedModified = 0.0F;
    }

    private static void stowDriverEquipmentOnce(LOTREntityNPC driver) {
        NBTTagCompound data = driver.getEntityData();
        if (!data.getBoolean(NBT_DRIVER_EQUIPMENT_STOWED)) {
            ItemStack heldItem = driver.getHeldItem();
            if (heldItem != null) {
                NBTTagCompound heldTag = new NBTTagCompound();
                heldItem.copy().writeToNBT(heldTag);
                data.setTag(NBT_DRIVER_STOWED_HELD_ITEM, heldTag);
            } else {
                data.removeTag(NBT_DRIVER_STOWED_HELD_ITEM);
            }

            NBTTagCompound npcItemsTag = new NBTTagCompound();
            driver.npcItemsInv.writeToNBT(npcItemsTag);
            data.setTag(NBT_DRIVER_STOWED_NPC_ITEMS, npcItemsTag);
            data.setBoolean(NBT_DRIVER_EQUIPMENT_STOWED, true);
        }

        clearDriverNpcItemState(driver);
        clearMountedDriverHeldItem(driver);
    }

    private static void clearDriverNpcItemState(LOTREntityNPC driver) {
        for (int slot = 0;
             slot < driver.npcItemsInv.getSizeInventory();
             ++slot) {
            if (driver.npcItemsInv.getStackInSlot(slot) != null) {
                driver.npcItemsInv.setInventorySlotContents(slot, null);
            }
        }
    }

    private static void suppressMountedDriverCombatState(
            LOTREntityNPC driver
    ) {
        clearDriverNpcItemState(driver);
        clearMountedDriverHeldItem(driver);
        driver.getNavigator().clearPathEntity();
        driver.moveForward = 0.0F;
        driver.moveStrafing = 0.0F;
    }

    private static void recoverOrphanedDriverEquipment(
            LOTREntityNPC driver
    ) {
        NBTTagCompound data = driver.getEntityData();
        if (!data.getBoolean(NBT_DRIVER_EQUIPMENT_STOWED)
                || driver.isDead) {
            return;
        }

        LOTREntityMumakil parent = findStoredDriverParent(driver);
        if (parent != null
                && isPhysicallyValidatedDriverAttachment(parent, driver)) {
            activateValidatedDriver(parent, driver);
            return;
        }

        long worldTick = driver.worldObj.getTotalWorldTime();
        boolean parentDefinitelyGone = parent != null
                && (parent.isDead || !parent.isEntityAlive());
        boolean relationshipDefinitelyEnded = driver.ticksExisted
                > DRIVER_ORPHAN_RECOVERY_GRACE_TICKS
                && (driver.ridingEntity != null
                || parent != null && parent.riddenByEntity != driver);
        if (!parentDefinitelyGone && !relationshipDefinitelyEnded) {
            Long orphanDeadline = DRIVER_ORPHAN_DEADLINES.get(driver);
            if (orphanDeadline == null) {
                DRIVER_ORPHAN_DEADLINES.put(
                        driver,
                        worldTick + DRIVER_ORPHAN_RECOVERY_GRACE_TICKS
                );
                return;
            }
            if (worldTick < orphanDeadline.longValue()) {
                return;
            }
        }

        restoreStowedDriverEquipment(driver);
    }

    private static LOTREntityMumakil findStoredDriverParent(
            LOTREntityNPC driver
    ) {
        NBTTagCompound data = driver.getEntityData();
        if (!data.hasKey(NBT_DRIVER_PARENT_UUID)
                || driver.worldObj == null) {
            return null;
        }

        UUID parentUuid;
        try {
            parentUuid = UUID.fromString(
                    data.getString(NBT_DRIVER_PARENT_UUID)
            );
        } catch (IllegalArgumentException ignored) {
            return null;
        }

        List loaded = driver.worldObj.loadedEntityList;
        for (int i = 0; i < loaded.size(); ++i) {
            Object candidate = loaded.get(i);
            if (candidate instanceof LOTREntityMumakil
                    && parentUuid.equals(
                    ((LOTREntityMumakil)candidate).getPersistentID()
            )) {
                return (LOTREntityMumakil)candidate;
            }
        }
        return null;
    }

    private static void restoreStowedDriverEquipment(
            LOTREntityNPC driver
    ) {
        NBTTagCompound data = driver.getEntityData();
        if (!data.getBoolean(NBT_DRIVER_EQUIPMENT_STOWED)) {
            return;
        }

        ItemStack heldItem = null;
        if (data.hasKey(NBT_DRIVER_STOWED_HELD_ITEM, 10)) {
            heldItem = ItemStack.loadItemStackFromNBT(
                    data.getCompoundTag(NBT_DRIVER_STOWED_HELD_ITEM)
            );
        }
        if (data.hasKey(NBT_DRIVER_STOWED_NPC_ITEMS, 10)) {
            driver.npcItemsInv.readFromNBT(
                    data.getCompoundTag(NBT_DRIVER_STOWED_NPC_ITEMS)
            );
        }

        data.removeTag(NBT_DRIVER_STOWED_HELD_ITEM);
        data.removeTag(NBT_DRIVER_STOWED_NPC_ITEMS);
        data.removeTag(NBT_DRIVER_PARENT_UUID);
        DRIVER_ORPHAN_DEADLINES.remove(driver);
        data.setBoolean(NBT_DRIVER_EQUIPMENT_STOWED, false);
        data.setBoolean(NBT_DRIVER_ACTIVE, false);

        driver.refreshCurrentAttackMode();
        driver.setCurrentItemOrArmor(
                0,
                heldItem == null ? null : heldItem.copy()
        );
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onDriverOrMumakDeath(LivingDeathEvent event) {
        if (event == null
                || event.entityLiving == null
                || event.entityLiving.worldObj == null
                || event.entityLiving.worldObj.isRemote
                || event.isCanceled()) {
            return;
        }

        if (event.entityLiving instanceof LOTREntityNPC) {
            restoreStowedDriverEquipment(
                    (LOTREntityNPC)event.entityLiving
            );
            return;
        }

        if (event.entityLiving instanceof LOTREntityMumakil
                && event.entityLiving.riddenByEntity
                instanceof LOTREntityNPC) {
            LOTREntityNPC driver =
                    (LOTREntityNPC)event.entityLiving.riddenByEntity;
            if (driver.getEntityData().getBoolean(
                    NBT_DRIVER_EQUIPMENT_STOWED
            )) {
                driver.mountEntity(null);
                restoreStowedDriverEquipment(driver);
            }
        }
    }

    @SubscribeEvent
    public void onFormationMemberHurt(LivingHurtEvent event) {
        if (event == null
                || event.entityLiving == null
                || event.entityLiving.worldObj == null
                || event.entityLiving.worldObj.isRemote
                || event.source == null) {
            return;
        }

        Entity sourceEntity = event.source.getEntity();
        if (!(sourceEntity instanceof EntityLivingBase)) {
            return;
        }

        LOTREntityMumakil mumakil = getHiredFormationMumakil(event.entityLiving);
        if (mumakil != null) {
            mumakil.recordRecentFormationThreat((EntityLivingBase)sourceEntity);
        }
    }

    private static LOTREntityMumakil getHiredFormationMumakil(EntityLivingBase victim) {
        LOTREntityMumakil mumakil = null;
        if (victim instanceof LOTREntityMumakil) {
            mumakil = (LOTREntityMumakil)victim;
        } else if (victim instanceof LOTREntityMumakilHowdahArcher) {
            LOTREntityMumakilHowdahArcher archer =
                    (LOTREntityMumakilHowdahArcher)victim;
            Entity mount = victim.worldObj.getEntityByID(archer.getHowdahMountEntityId());
            if (archer.hasActiveHowdahAttachment()
                    && mount instanceof LOTREntityMumakil) {
                mumakil = (LOTREntityMumakil)mount;
            }
        } else if (victim instanceof LOTREntityNPC
                && victim.ridingEntity instanceof LOTREntityMumakil) {
            LOTREntityMumakil riddenMumakil =
                    (LOTREntityMumakil)victim.ridingEntity;
            if (riddenMumakil.riddenByEntity == victim) {
                mumakil = riddenMumakil;
            }
        }

        return mumakil != null && mumakil.isHiredWarMumakil()
                ? mumakil
                : null;
    }

    private static void ensureMountedDriverCombatGuard(LOTREntityNPC driver) {
        if (MOUNTED_DRIVER_COMBAT_GUARDS.containsKey(driver)) {
            return;
        }

        driver.tasks.addTask(0, new EntityAIBlockMountedMumakilDriverCombat(driver));
        MOUNTED_DRIVER_COMBAT_GUARDS.put(driver, Boolean.TRUE);
    }

    private static final class EntityAIBlockMountedMumakilDriverCombat extends EntityAIBase {
        private final LOTREntityNPC driver;

        private EntityAIBlockMountedMumakilDriverCombat(LOTREntityNPC driver) {
            this.driver = driver;
            this.setMutexBits(3);
        }

        @Override
        public boolean shouldExecute() {
            return getPhysicallyValidatedDriverMumakil(this.driver)
                    != null;
        }

        @Override
        public boolean continueExecuting() {
            return this.shouldExecute();
        }

    }

    private static void markHiredWarIfApplicable(LOTREntityMumakil mumakil) {
        if (isImplicitHiredWarMumakil(mumakil)) {
            mumakil.setHiredWarMumakil(true);
        }
    }

    private static boolean isImplicitHiredWarMumakil(LOTREntityMumakil mumakil) {
        return mumakil != null
                && mumakil.getBelongsToNPC()
                && mumakil.hasMumakilHowdahEquipped();
    }

    private static void updateDrivenMumakil(LOTREntityMumakil mumakil, LOTREntityNPC driver) {
        World world = mumakil.worldObj;
        long worldTick = world.getTotalWorldTime();
        boolean autonomousFormation =
                mumakil.isAutonomousWarFormation();
        boolean warCombatFormation =
                mumakil.isWarCombatFormation();
        EntityLivingBase currentTarget = getStoredDriverTarget(mumakil);
        EntityLivingBase authoritativeTarget = getAuthoritativeAttackTarget(mumakil, driver);

        if (MumakilPerformanceTracker.isEnabled()) {
            MumakilPerformanceTracker.recordDriverTargetRead(mumakil);
        }

        if (currentTarget == null
                && getDriverTargetRuntimeState(mumakil).targetEntityId > 0) {
            if (MumakilPerformanceTracker.isEnabled()) {
                MumakilPerformanceTracker.recordDriverTargetReset(mumakil);
            }
            clearStoredDriverTarget(mumakil);
        }

        /*
         * Follow native LOTR target ownership: when the mounted NPC releases
         * its target, do not immediately write the handler's cached target
         * back onto it. Clear the shared target and let the bounded scan choose
         * a current nearby enemy again. This prevents stale target fixation.
         */
        if (driver != null
                && authoritativeTarget == null
                && currentTarget != null) {
            clearAuthoritativeAttackTarget(mumakil, driver, currentTarget);
            clearStoredDriverTarget(mumakil);
            scheduleImmediateDriverTargetScan(mumakil);
            currentTarget = null;
        }

        if (authoritativeTarget != null && authoritativeTarget != currentTarget) {
            if (isValidDriverTarget(mumakil, driver, authoritativeTarget)
                    && !isRejectedDriverTarget(mumakil, authoritativeTarget, worldTick)
                    && !isTooHighForDrivenMumakilMelee(mumakil, authoritativeTarget)) {
                setStoredDriverTarget(
                        mumakil,
                        authoritativeTarget
                );
                currentTarget = authoritativeTarget;
            } else {
                if (isTooHighForDrivenMumakilMelee(mumakil, authoritativeTarget)) {
                    temporarilyRejectDriverTarget(
                            mumakil,
                            authoritativeTarget,
                            worldTick,
                            DRIVER_TARGET_ELEVATED_REJECT_TICKS,
                            "elevated"
                    );
                }
                clearAuthoritativeAttackTarget(mumakil, driver, authoritativeTarget);
                if (driver != null) {
                    if (autonomousFormation) {
                        MumakilServerPerformanceDiagnostics
                                .recordAutonomousTargetReplacement(world);
                    }
                    clearStoredDriverTarget(mumakil);
                    scheduleImmediateDriverTargetScan(mumakil);
                    currentTarget = null;
                }
            }
        }

        if (currentTarget != null && !isValidDriverTarget(mumakil, driver, currentTarget)) {
            if (MumakilPerformanceTracker.isEnabled()) {
                MumakilPerformanceTracker.recordDriverTargetReset(mumakil);
            }
            if (autonomousFormation) {
                MumakilServerPerformanceDiagnostics
                        .recordAutonomousTargetReplacement(world);
            }
            clearStoredDriverTarget(mumakil);
            clearAuthoritativeAttackTarget(mumakil, driver, currentTarget);
            scheduleImmediateDriverTargetScan(mumakil);
            currentTarget = null;
        }

        if (currentTarget != null && isRejectedDriverTarget(mumakil, currentTarget, worldTick)) {
            if (MumakilPerformanceTracker.isEnabled()) {
                MumakilPerformanceTracker.recordDriverTargetReset(mumakil);
            }
            if (autonomousFormation) {
                MumakilServerPerformanceDiagnostics
                        .recordAutonomousTargetReplacement(world);
            }
            clearStoredDriverTarget(mumakil);
            clearAuthoritativeAttackTarget(mumakil, driver, currentTarget);
            scheduleImmediateDriverTargetScan(mumakil);
            currentTarget = null;
        }

        if (currentTarget != null
                && warCombatFormation
                && !shouldRetainDriverTarget(
                mumakil,
                driver,
                currentTarget
        )) {
            if (autonomousFormation) {
                MumakilServerPerformanceDiagnostics
                        .recordAutonomousTargetReplacement(world);
            }
            clearStoredDriverTarget(mumakil);
            clearAuthoritativeAttackTarget(
                    mumakil,
                    driver,
                    currentTarget
            );
            scheduleImmediateDriverTargetScan(mumakil);
            currentTarget = null;
        }

        if (currentTarget != null && isTooHighForDrivenMumakilMelee(mumakil, currentTarget)) {
            if (MumakilPerformanceTracker.isEnabled()) {
                MumakilPerformanceTracker.recordDriverTargetReset(mumakil);
            }
            if (autonomousFormation) {
                MumakilServerPerformanceDiagnostics
                        .recordAutonomousTargetReplacement(world);
            }
            temporarilyRejectDriverTarget(
                    mumakil,
                    currentTarget,
                    worldTick,
                    DRIVER_TARGET_ELEVATED_REJECT_TICKS,
                    "elevated"
            );

            clearAuthoritativeAttackTarget(mumakil, driver, currentTarget);
            clearStoredDriverTarget(mumakil);
            scheduleImmediateDriverTargetScan(mumakil);
            return;
        }

        if (currentTarget != null) {
            updateCurrentTargetProgress(mumakil, driver, currentTarget, worldTick);

            /*
             * updateCurrentTargetProgress may clear/reject the current target.
             */
            currentTarget = getStoredDriverTarget(mumakil);
            if (MumakilPerformanceTracker.isEnabled()) {
                MumakilPerformanceTracker.recordDriverTargetRead(mumakil);
            }
        }

        if (currentTarget == null) {
            if (driver != null) {
                EntityLivingBase formationThreat = mumakil.getRecentFormationThreat();
                if (isValidDriverTarget(mumakil, driver, formationThreat)
                        && !isRejectedDriverTarget(mumakil, formationThreat, worldTick)
                        && !isTooHighForDrivenMumakilMelee(mumakil, formationThreat)) {
                    currentTarget = formationThreat;
                }
            }

            if (currentTarget == null
                    && (driver == null
                    || warCombatFormation)) {
                long acquisitionStart =
                        MumakilServerPerformanceDiagnostics
                                .startTimer(world);
                /*
                 * Pass the live formation driver through native LOTR faction
                 * validation. The null form retains the established
                 * driverless-formation rules.
                 */
                currentTarget = findNewDriverTarget(
                        mumakil,
                        driver,
                        worldTick
                );
                if (autonomousFormation) {
                    MumakilServerPerformanceDiagnostics
                            .recordAutonomousTargetAcquisition(
                                    world,
                                    System.nanoTime()
                                            - acquisitionStart,
                                    currentTarget != null
                            );
                }
            }

            if (currentTarget != null) {
                setStoredDriverTarget(
                        mumakil,
                        currentTarget
                );
            }
        }

        if (currentTarget != null) {
            setAuthoritativeAttackTarget(mumakil, driver, currentTarget);
        }
    }

    private static LOTREntityNPC getValidNearHaradDriver(LOTREntityMumakil mumakil) {
        Entity rider = mumakil.riddenByEntity;

        if (!(rider instanceof LOTREntityNPC)
                || !isPhysicallyValidatedDriverAttachment(
                mumakil,
                (LOTREntityNPC)rider
        )) {
            return null;
        }
        return (LOTREntityNPC)rider;
    }

    private static LOTREntityMumakil getPhysicallyValidatedDriverMumakil(
            LOTREntityNPC driver
    ) {
        if (driver == null
                || !(driver.ridingEntity instanceof LOTREntityMumakil)) {
            return null;
        }
        LOTREntityMumakil mumakil =
                (LOTREntityMumakil)driver.ridingEntity;
        return isPhysicallyValidatedDriverAttachment(mumakil, driver)
                ? mumakil
                : null;
    }

    private static boolean isPhysicallyValidatedDriverAttachment(
            LOTREntityMumakil mumakil,
            LOTREntityNPC driver
    ) {
        return mumakil != null
                && driver instanceof LOTREntitySouthronChampion
                && mumakil.worldObj != null
                && driver.worldObj == mumakil.worldObj
                && !mumakil.isDead
                && mumakil.isEntityAlive()
                && !driver.isDead
                && driver.isEntityAlive()
                && driver.ridingEntity == mumakil
                && mumakil.riddenByEntity == driver
                && mumakil.isHiredWarMumakil()
                && mumakil.getBelongsToNPC()
                && mumakil.hasMumakilHowdahEquipped()
                && isNearHaradOrSouthronNPC(driver);
    }

    private static boolean isNearHaradOrSouthronNPC(LOTREntityNPC npc) {
        try {
            LOTRFaction faction = LOTRMod.getNPCFaction(npc);
            if (faction == LOTRFaction.NEAR_HARAD) {
                return true;
            }
        } catch (Exception e) {
            /*
             * Fall through to class-name fallback.
             */
        }

        /*
         * Fallback for addon/UCP/deobf naming variations.
         */
        String name = npc.getClass().getName().toLowerCase();
        return name.contains("southron") || name.contains("nearharad") || name.contains("near_harad") || name.contains("harad");
    }

    private static EntityLivingBase getStoredDriverTarget(LOTREntityMumakil mumakil) {
        int targetId = getDriverTargetRuntimeState(mumakil).targetEntityId;

        if (targetId <= 0 || mumakil.worldObj == null) {
            return null;
        }

        Entity entity = mumakil.worldObj.getEntityByID(targetId);
        return entity instanceof EntityLivingBase ? (EntityLivingBase) entity : null;
    }

    /**
     * Returns true only for the target currently owned by the handler after
     * its normal validation, rejection, retention, and driver/mount sync
     * checks. Follow AI uses this to avoid treating a stale native driver
     * target as active combat.
     */
    public static boolean hasActiveAuthoritativeHiredWarCombatTarget(
            LOTREntityMumakil mumakil
    ) {
        if (mumakil == null
                || mumakil.worldObj == null
                || mumakil.worldObj.isRemote
                || !mumakil.isHiredWarMumakil()) {
            return false;
        }

        LOTREntityNPC driver = getValidNearHaradDriver(mumakil);
        if (driver == null
                || driver.hiredNPCInfo == null
                || !driver.hiredNPCInfo.isActive) {
            return false;
        }

        EntityLivingBase target = getStoredDriverTarget(mumakil);
        long worldTick = mumakil.worldObj.getTotalWorldTime();
        DriverTargetRuntimeState state =
                getDriverTargetRuntimeState(mumakil);
        double retentionRange = getDriverTargetRetentionRange(mumakil);
        if (target == null
                || !isValidDriverTarget(mumakil, driver, target)
                || isRejectedDriverTarget(mumakil, target, worldTick)
                || isTooHighForDrivenMumakilMelee(mumakil, target)
                || mumakil.getDistanceSqToEntity(target)
                > retentionRange * retentionRange
                || state.retentionTargetEntityId == target.getEntityId()
                && state.unseenTargetTicks
                > DRIVER_TARGET_UNSEEN_MEMORY_TICKS) {
            return false;
        }

        return driver.getAttackTarget() == target
                && mumakil.getAttackTarget() == target;
    }

    private static boolean setStoredDriverTarget(
            LOTREntityMumakil mumakil,
            EntityLivingBase target
    ) {
        if (target == null) {
            clearStoredDriverTarget(mumakil);
            return false;
        }

        DriverTargetRuntimeState state =
                getDriverTargetRuntimeState(mumakil);
        int targetId = target.getEntityId();
        if (state.retentionTargetEntityId != targetId) {
            state.retentionTargetEntityId = targetId;
            state.unseenTargetTicks = 0;
        }
        boolean storedTargetChanged = state.targetEntityId != targetId;

        if (storedTargetChanged) {
            state.targetEntityId = targetId;
            resetTargetProgress(mumakil, target);
        }

        if (DEBUG_DRIVER_TARGETS && storedTargetChanged) {
            System.out.println(
                    "[LOTRMoreMobs][MumakAutonomousCombat]"
                            + " mumak=" + mumakil.getEntityId()
                            + " origin=" + mumakil.getFormationOrigin()
                            + " hiredMode="
                            + mumakil.isHiredWarMumakil()
                            + " driver="
                            + (mumakil.riddenByEntity == null
                            ? -1
                            : mumakil.riddenByEntity.getEntityId())
                            + " target=" + targetId
                            + " targetClass="
                            + target.getClass().getSimpleName()
                            + " distance="
                            + Math.sqrt(
                                    mumakil.getDistanceSqToEntity(
                                            target
                                    )
                            )
                            + " cooldown="
                            + mumakil.getTuskAttackCooldownTicks()
                            + " combatPass="
                            + mumakil
                            .isAutonomousCombatPassActive()
                            + " hasPath="
                            + !mumakil.getNavigator().noPath()
                            + " aiSpeed="
                            + mumakil.getAIMoveSpeed()
                            + " moveForward="
                            + mumakil.moveForward
                            + " horizontalMotion="
                            + Math.sqrt(
                                    mumakil.motionX
                                            * mumakil.motionX
                                            + mumakil.motionZ
                                            * mumakil.motionZ
                            )
            );
        }
        return storedTargetChanged;
    }

    private static void observeMountedDriverTargetAcquisition(
            LOTREntityMumakil mumakil,
            LOTREntityNPC driver
    ) {
        DriverTargetRuntimeState state =
                getDriverTargetRuntimeState(mumakil);
        EntityLivingBase target = driver == null
                ? null
                : driver.getAttackTarget();
        int currentTargetId = target != null
                && target.isEntityAlive()
                ? target.getEntityId()
                : 0;
        int observedTargetId = state.observedTargetEntityId;

        long worldTick = mumakil.worldObj.getTotalWorldTime();
        if (currentTargetId <= 0) {
            long lostSinceTick = state.targetLostSinceTick;
            if (lostSinceTick <= 0L) {
                state.targetLostSinceTick = worldTick;
            } else if (worldTick - lostSinceTick
                    >= MOUNTED_DRIVER_TARGET_LOSS_CONFIRM_TICKS) {
                state.observedTargetEntityId = 0;
                state.targetLostSinceTick = 0L;
            }
            return;
        }

        state.targetLostSinceTick = 0L;
        if (currentTargetId == observedTargetId) {
            return;
        }

        state.observedTargetEntityId = currentTargetId;

        logDriverHorn(
                mumakil,
                driver,
                "genuine target change "
                        + observedTargetId
                        + " -> "
                        + currentTargetId
        );
        tryPlayMountedDriverHornForNewTarget(
                mumakil,
                driver,
                target,
                worldTick
        );
    }

    private static void tryPlayMountedDriverHornForNewTarget(
            LOTREntityMumakil mumakil,
            LOTREntityNPC driver,
            EntityLivingBase target,
            long worldTick
    ) {
        if (mumakil == null
                || driver == null
                || target == null
                || !target.isEntityAlive()
                || mumakil.worldObj == null
                || mumakil.worldObj.isRemote
                || mumakil.riddenByEntity != driver
                || !driver.isEntityAlive()) {
            return;
        }

        if (mumakil.getMountedDriverHornTicks() > 0) {
            logDriverHorn(
                    mumakil,
                    driver,
                    "driver horn already active"
            );
            return;
        }

        long nextDriverHornTick =
                driver.getEntityData().getLong(
                        NBT_MOUNTED_DRIVER_NEXT_HORN_TICK
                );
        if (worldTick < nextDriverHornTick) {
            logDriverHorn(
                    mumakil,
                    driver,
                    "driver cooldown rejection remaining="
                            + (nextDriverHornTick - worldTick)
            );
            return;
        }

        MountedDriverHornWorldState worldState =
                MOUNTED_DRIVER_HORN_WORLD_STATES.get(
                        mumakil.worldObj
                );
        if (worldState == null) {
            worldState = new MountedDriverHornWorldState();
            MOUNTED_DRIVER_HORN_WORLD_STATES.put(
                    mumakil.worldObj,
                    worldState
            );
        }
        if (worldTick < worldState.quietUntilTick) {
            logDriverHorn(
                    mumakil,
                    driver,
                    "world quiet rejection remaining="
                            + (worldState.quietUntilTick - worldTick)
            );
            return;
        }

        int chanceRoll = mumakil.getRNG().nextInt(
                MOUNTED_DRIVER_HORN_CHANCE_DENOMINATOR
        );
        logDriverHorn(
                mumakil,
                driver,
                "chance roll="
                        + chanceRoll
                        + "/"
                        + MOUNTED_DRIVER_HORN_CHANCE_DENOMINATOR
        );
        if (chanceRoll != 0) {
            return;
        }

        driver.getEntityData().setLong(
                NBT_MOUNTED_DRIVER_NEXT_HORN_TICK,
                worldTick + MOUNTED_DRIVER_HORN_COOLDOWN_TICKS
        );
        worldState.quietUntilTick =
                worldTick
                        + MOUNTED_DRIVER_HORN_WORLD_COOLDOWN_TICKS;

        mumakil.setMountedDriverHornTicks(
                MOUNTED_DRIVER_HORN_DISPLAY_TICKS
        );
        clearMountedDriverHeldItem(driver);
        logDriverHorn(
                mumakil,
                driver,
                "horn start target=" + target.getEntityId()
        );
        /*
         * Selection, pose, and this single positional sound all begin on the
         * server in one event, so clients hear it through normal world sound
         * propagation and there is nothing to replay after a chunk reload.
         */
        mumakil.worldObj.playSoundAtEntity(
                driver,
                MOUNTED_DRIVER_HORN_SOUND,
                4.0F,
                1.0F
        );
        logDriverHorn(
                mumakil,
                driver,
                "sound playback " + MOUNTED_DRIVER_HORN_SOUND
        );
    }

    private static void logDriverHorn(
            LOTREntityMumakil mumakil,
            LOTREntityNPC driver,
            String message
    ) {
        if (!DEBUG_DRIVER_HORN) {
            return;
        }
        System.out.println(
                "[LOTRMoreMobs][MumakDriverHorn] mount="
                        + (mumakil == null
                        ? -1
                        : mumakil.getEntityId())
                        + " driver="
                        + (driver == null
                        ? -1
                        : driver.getEntityId())
                        + " "
                        + message
        );
    }

    private static void clearStoredDriverTarget(LOTREntityMumakil mumakil) {
        DriverTargetRuntimeState state =
                getDriverTargetRuntimeState(mumakil);

        if (state.targetEntityId <= 0
                && !hasActiveDriverTargetProgress(mumakil.getDriverTargetProgressState())) {
            return;
        }

        state.targetEntityId = -1;
        state.retentionTargetEntityId = -1;
        state.unseenTargetTicks = 0;
        mumakil.getDriverTargetProgressState().reset();
    }

    private static void clearDriverTargetState(LOTREntityMumakil mumakil) {
        DriverTargetRuntimeState state =
                getDriverTargetRuntimeState(mumakil);
        NBTTagCompound data = mumakil.getEntityData();
        state.targetEntityId = -1;
        state.rejectedTargetEntityId = -1;
        state.rejectedUntilTick = 0L;
        state.retentionTargetEntityId = -1;
        state.unseenTargetTicks = 0;
        data.setLong(NBT_NEXT_TARGET_SCAN_TICK, 0L);
        mumakil.getDriverTargetProgressState().reset();
    }

    private static boolean hasDriverTargetState(LOTREntityMumakil mumakil) {
        DriverTargetRuntimeState state =
                DRIVER_TARGET_RUNTIME_STATES.get(mumakil);
        NBTTagCompound data = mumakil.getEntityData();
        return (state != null
                && (state.targetEntityId > 0
                || state.rejectedTargetEntityId > 0
                || state.rejectedUntilTick > 0L))
                || data.getLong(NBT_NEXT_TARGET_SCAN_TICK) > 0L
                || hasActiveDriverTargetProgress(mumakil.getDriverTargetProgressState());
    }

    private static boolean hasActiveDriverTargetProgress(LOTREntityMumakil.DriverTargetProgressState state) {
        return state.progressTargetEntityId > 0
                || state.nextProgressCheckTick > 0L
                || state.stuckTicks > 0;
    }

    private static EntityLivingBase getAuthoritativeAttackTarget(
            LOTREntityMumakil mumakil,
            LOTREntityNPC driver
    ) {
        return driver != null ? driver.getAttackTarget() : mumakil.getAttackTarget();
    }

    private static void setAuthoritativeAttackTarget(
            LOTREntityMumakil mumakil,
            LOTREntityNPC driver,
            EntityLivingBase target
    ) {
        boolean authoritativeTargetChanged = getAuthoritativeAttackTarget(mumakil, driver) != target;
        boolean mountTargetChanged = driver != null && mumakil.getAttackTarget() != target;

        if (!authoritativeTargetChanged && !mountTargetChanged) {
            return;
        }

        if (MumakilPerformanceTracker.isEnabled()) {
            MumakilPerformanceTracker.recordDriverTargetSyncAttempt(mumakil);
        }

        if (driver != null) {
            if (authoritativeTargetChanged) {
                driver.setAttackTarget(target);
            }
            if (mountTargetChanged) {
                mumakil.setAttackTarget(target);
            }
        } else if (authoritativeTargetChanged) {
            mumakil.setAttackTarget(target);
        }
    }

    private static void clearAuthoritativeAttackTarget(
            LOTREntityMumakil mumakil,
            LOTREntityNPC driver,
            EntityLivingBase target
    ) {
        if (driver != null && driver.getAttackTarget() == target) {
            driver.setAttackTarget(null);
        }
        if (mumakil.getAttackTarget() == target) {
            mumakil.setAttackTarget(null);
        }
    }

    private static void resetTargetProgress(LOTREntityMumakil mumakil, EntityLivingBase target) {
        LOTREntityMumakil.DriverTargetProgressState state = mumakil.getDriverTargetProgressState();
        state.progressTargetEntityId = target != null ? target.getEntityId() : -1;
        state.nextProgressCheckTick = mumakil.worldObj.getTotalWorldTime() + DRIVER_TARGET_PROGRESS_CHECK_INTERVAL;
        state.lastProgressX = mumakil.posX;
        state.lastProgressY = mumakil.posY;
        state.lastProgressZ = mumakil.posZ;
        state.stuckTicks = 0;
    }

    public static void rejectAutonomousTargetAfterCombatPathFailure(
            LOTREntityMumakil mumakil,
            EntityLivingBase target
    ) {
        if (mumakil == null
                || target == null
                || mumakil.worldObj == null
                || mumakil.worldObj.isRemote
                || !mumakil.isWarCombatFormation()
                || mumakil.riddenByEntity instanceof EntityPlayer) {
            return;
        }

        LOTREntityNPC driver =
                mumakil.riddenByEntity instanceof LOTREntityNPC
                        && mumakil.riddenByEntity.isEntityAlive()
                        ? (LOTREntityNPC)mumakil.riddenByEntity
                        : null;
        if (mumakil.getAttackTarget() != target
                || (driver != null
                && driver.getAttackTarget() != target)) {
            return;
        }

        long worldTick = mumakil.worldObj.getTotalWorldTime();
        temporarilyRejectDriverTarget(
                mumakil,
                target,
                worldTick,
                DRIVER_TARGET_REJECT_TICKS,
                "combat-waypoint-failures"
        );
        if (mumakil.isAutonomousWarFormation()) {
            MumakilServerPerformanceDiagnostics
                    .recordAutonomousTargetReplacement(
                            mumakil.worldObj
                    );
        }
        clearAuthoritativeAttackTarget(
                mumakil,
                driver,
                target
        );
        clearStoredDriverTarget(mumakil);
        resetTargetProgress(mumakil, null);
    }

    private static void updateCurrentTargetProgress(
            LOTREntityMumakil mumakil,
            LOTREntityNPC driver,
            EntityLivingBase target,
            long worldTick
    ) {
        if (target == null) {
            return;
        }

        LOTREntityMumakil.DriverTargetProgressState state = mumakil.getDriverTargetProgressState();

        if (worldTick < state.nextProgressCheckTick) {
            return;
        }

        state.nextProgressCheckTick = worldTick + DRIVER_TARGET_PROGRESS_CHECK_INTERVAL;
        if (MumakilPerformanceTracker.isEnabled()) {
            MumakilPerformanceTracker.recordUnreachableCheck(mumakil);
        }

        double distSq = mumakil.getDistanceSqToEntity(target);
        double directReach = APPROACH_STOP_RANGE + DRIVER_TARGET_REACHABLE_EXTRA_RANGE;
        double directReachSq = directReach * directReach;

        if (distSq <= directReachSq) {
            resetTargetProgress(mumakil, target);
            return;
        }

        int targetId = target.getEntityId();

        if (state.progressTargetEntityId != targetId) {
            resetTargetProgress(mumakil, target);
            return;
        }

        double progressX = mumakil.posX - state.lastProgressX;
        double progressZ = mumakil.posZ - state.lastProgressZ;
        double horizontalProgressSq = progressX * progressX + progressZ * progressZ;

        if (horizontalProgressSq >= DRIVER_PROGRESS_MOVE_THRESHOLD_SQ) {
            state.stuckTicks = 0;
        } else {
            state.stuckTicks += DRIVER_TARGET_PROGRESS_CHECK_INTERVAL;
        }

        state.lastProgressX = mumakil.posX;
        state.lastProgressY = mumakil.posY;
        state.lastProgressZ = mumakil.posZ;

        if (state.stuckTicks >= DRIVER_TARGET_STUCK_TIMEOUT) {
            if (MumakilPerformanceTracker.isEnabled()) {
                MumakilPerformanceTracker.recordDriverTargetReset(mumakil);
            }
            temporarilyRejectDriverTarget(
                    mumakil,
                    target,
                    worldTick,
                    DRIVER_TARGET_REJECT_TICKS,
                    "stuck"
            );

            if (mumakil.isAutonomousWarFormation()) {
                MumakilServerPerformanceDiagnostics
                        .recordAutonomousTargetReplacement(
                                mumakil.worldObj
                        );
            }
            clearAuthoritativeAttackTarget(mumakil, driver, target);
            clearStoredDriverTarget(mumakil);
            scheduleImmediateDriverTargetScan(mumakil);
        }
    }

    private static void temporarilyRejectDriverTarget(
            LOTREntityMumakil mumakil,
            EntityLivingBase target,
            long worldTick,
            int ticks,
            String reason
    ) {
        if (target == null) {
            return;
        }

        DriverTargetRuntimeState state =
                getDriverTargetRuntimeState(mumakil);
        state.rejectedTargetEntityId = target.getEntityId();
        state.rejectedUntilTick = worldTick + ticks;

        if (DEBUG_DRIVER_TARGETS) {
            System.out.println("[LOTRMoreMobs] Driven Mumakil " + mumakil.getEntityId()
                    + " rejected target " + target.getEntityId()
                    + " for " + ticks + " ticks reason=" + reason);
        }
    }

    private static boolean isRejectedDriverTarget(LOTREntityMumakil mumakil, EntityLivingBase target, long worldTick) {
        if (target == null) {
            return false;
        }

        DriverTargetRuntimeState state =
                getDriverTargetRuntimeState(mumakil);
        int rejectedId = state.rejectedTargetEntityId;
        long rejectedUntil = state.rejectedUntilTick;

        if (rejectedId <= 0 || worldTick >= rejectedUntil) {
            if (rejectedId > 0 && worldTick >= rejectedUntil) {
                state.rejectedTargetEntityId = -1;
                state.rejectedUntilTick = 0L;
            }
            return false;
        }

        return rejectedId == target.getEntityId();
    }

    private static void scheduleImmediateDriverTargetScan(
            LOTREntityMumakil mumakil
    ) {
        if (mumakil != null) {
            mumakil.getEntityData().setLong(
                    NBT_NEXT_TARGET_SCAN_TICK,
                    0L
            );
        }
    }

    private static double getDriverTargetRetentionRange(
            LOTREntityMumakil mumakil
    ) {
        if (mumakil != null
                && mumakil.getEntityAttribute(
                SharedMonsterAttributes.followRange
        ) != null) {
            return Math.max(
                    TARGET_SCAN_RANGE,
                    mumakil.getEntityAttribute(
                            SharedMonsterAttributes.followRange
                    ).getAttributeValue()
            );
        }
        return 32.0D;
    }

    private static boolean shouldRetainDriverTarget(
            LOTREntityMumakil mumakil,
            LOTREntityNPC driver,
            EntityLivingBase target
    ) {
        if (mumakil == null || target == null) {
            return false;
        }

        double retentionRange = getDriverTargetRetentionRange(mumakil);
        if (mumakil.getDistanceSqToEntity(target)
                > retentionRange * retentionRange) {
            return false;
        }

        DriverTargetRuntimeState state =
                getDriverTargetRuntimeState(mumakil);
        int targetId = target.getEntityId();
        if (state.retentionTargetEntityId != targetId) {
            state.retentionTargetEntityId = targetId;
            state.unseenTargetTicks = 0;
        }

        boolean canSee = driver != null
                ? driver.getEntitySenses().canSee(target)
                : mumakil.getEntitySenses().canSee(target);
        if (canSee) {
            state.unseenTargetTicks = 0;
            return true;
        }

        ++state.unseenTargetTicks;
        return state.unseenTargetTicks
                <= DRIVER_TARGET_UNSEEN_MEMORY_TICKS;
    }

    private static boolean canSeeNewDriverTarget(
            LOTREntityMumakil mumakil,
            EntityLivingBase driver,
            EntityLivingBase target
    ) {
        if (target == mumakil.getRecentFormationThreat()) {
            return true;
        }
        if (driver instanceof EntityLiving) {
            return ((EntityLiving)driver).getEntitySenses().canSee(target);
        }
        return mumakil.getEntitySenses().canSee(target);
    }

    private static EntityLivingBase findNewDriverTarget(LOTREntityMumakil mumakil, EntityLivingBase driver, long worldTick) {
        NBTTagCompound data = mumakil.getEntityData();

        if (worldTick < data.getLong(NBT_NEXT_TARGET_SCAN_TICK)) {
            return null;
        }

        data.setLong(NBT_NEXT_TARGET_SCAN_TICK, worldTick + DRIVER_TARGET_SCAN_COOLDOWN);

        AxisAlignedBB scanBox = mumakil.boundingBox.expand(
                TARGET_SCAN_RANGE,
                TARGET_SCAN_VERTICAL_RANGE,
                TARGET_SCAN_RANGE
        );

        boolean trackPerformance =
                MumakilPerformanceTracker.isEnabled();
        long serverTimingStart =
                MumakilServerPerformanceDiagnostics.startTimer(
                        mumakil.worldObj
                );
        long perfStart = trackPerformance
                ? MumakilPerformanceTracker.startTimer()
                : 0L;
        List nearby = mumakil.worldObj.getEntitiesWithinAABB(EntityLivingBase.class, scanBox);

        EntityLivingBase bestTarget = null;
        int bestScore = Integer.MIN_VALUE;
        double bestDistanceSq = Double.MAX_VALUE;

        for (int i = 0; i < nearby.size(); ++i) {
            EntityLivingBase candidate = (EntityLivingBase) nearby.get(i);

            if (!isValidDriverTarget(mumakil, driver, candidate)) {
                continue;
            }

            if (!canSeeNewDriverTarget(mumakil, driver, candidate)) {
                continue;
            }

            if (isRejectedDriverTarget(mumakil, candidate, worldTick)) {
                continue;
            }

            if (isTooHighForDrivenMumakilMelee(mumakil, candidate)) {
                continue;
            }

            double distanceSq = mumakil.getDistanceSqToEntity(candidate);
            int score = getDriverTargetScore(mumakil, driver, candidate, distanceSq);

            if (score > bestScore || score == bestScore && distanceSq < bestDistanceSq) {
                bestTarget = candidate;
                bestScore = score;
                bestDistanceSq = distanceSq;
            }
        }

        if (trackPerformance) {
            MumakilPerformanceTracker.recordMountTargetScan(mumakil, nearby.size(), System.nanoTime() - perfStart);
        }
        MumakilServerPerformanceDiagnostics.recordDriverTargetScan(
                mumakil.worldObj,
                System.nanoTime() - serverTimingStart
        );

        return bestTarget;
    }

    private static int getDriverTargetScore(
            LOTREntityMumakil mumakil,
            EntityLivingBase driver,
            EntityLivingBase candidate,
            double distanceSq
    ) {
        int score = 0;

        if (isAttacking(candidate, mumakil)) {
            score += 1000;
        }

        if (isAttacking(candidate, driver)) {
            score += 850;
        }

        if (isAttackingAttachedArcher(candidate, mumakil)) {
            score += 900;
        }

        if (candidate instanceof EntityPlayer) {
            score += 150;
        } else if (candidate instanceof LOTREntityNPC) {
            score += 120;
        } else if (candidate instanceof IMob) {
            score += 60;
        }

        double directReach = APPROACH_STOP_RANGE + DRIVER_TARGET_REACHABLE_EXTRA_RANGE;
        if (distanceSq <= directReach * directReach) {
            score += 300;
        } else if (distanceSq <= 18.0D * 18.0D) {
            score += 120;
        }

        if (mumakil.canEntityBeSeen(candidate)) {
            score += 40;
        }

        /*
         * Light distance bias without making distance beat "this enemy is attacking us."
         */
        score -= MathHelper.floor_double(Math.sqrt(distanceSq));

        return score;
    }

    private static boolean isAttacking(EntityLivingBase attacker, EntityLivingBase victim) {
        if (attacker == null || victim == null) {
            return false;
        }

        if (attacker instanceof EntityLiving) {
            EntityLiving living = (EntityLiving) attacker;
            return living.getAttackTarget() == victim;
        }

        return false;
    }

    private static boolean isAttackingAttachedArcher(EntityLivingBase attacker, LOTREntityMumakil mumakil) {
        if (!(attacker instanceof EntityLiving) || mumakil == null) {
            return false;
        }

        EntityLivingBase victim = ((EntityLiving)attacker).getAttackTarget();
        return victim instanceof LOTREntityMumakilHowdahArcher
                && ((LOTREntityMumakilHowdahArcher)victim).getHowdahMountEntityId()
                == mumakil.getEntityId();
    }

    private static boolean isValidDriverTarget(
            LOTREntityMumakil mumakil,
            EntityLivingBase driver,
            EntityLivingBase target
    ) {
        if (MumakilPerformanceTracker.isEnabled()) {
            MumakilPerformanceTracker.recordMountCandidateCheck(mumakil);
        }

        if (mumakil == null || target == null) {
            return false;
        }

        if (target == mumakil || target == driver) {
            return false;
        }

        if (target.isDead
                || !target.isEntityAlive()
                || mumakil.worldObj == null
                || target.worldObj != mumakil.worldObj
                || target.getEntityId() <= 0
                || mumakil.worldObj.getEntityByID(
                target.getEntityId()
        ) != target) {
            return false;
        }

        if (target instanceof LOTREntityMumakil) {
            return false;
        }

        if (target instanceof LOTREntityMumakilHowdahArcher) {
            return false;
        }

        boolean retaliationThreat =
                target == mumakil.getRecentFormationThreat();

        if ((target instanceof EntityAnimal
                || target instanceof LOTRAmbientCreature)
                && !retaliationThreat
                && !isAttacking(target, mumakil)
                && !isAttacking(target, driver)
                && !isAttackingAttachedArcher(target, mumakil)) {
            return false;
        }

        if (target.riddenByEntity != null || target.ridingEntity != null) {
            return false;
        }

        if (target instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) target;
            if (player.capabilities.isCreativeMode) {
                return false;
            }

            if (driver == null) {
                return target == mumakil.getRecentFormationThreat();
            }

            if (driver instanceof LOTREntityNPC) {
                UUID hiringPlayerId =
                        ((LOTREntityNPC)driver).hiredNPCInfo.getHiringPlayerUUID();
                if (hiringPlayerId != null
                        && hiringPlayerId.equals(player.getUniqueID())) {
                    return false;
                }
            }
        }

        if (target instanceof EntityTameable && ((EntityTameable) target).isTamed()) {
            return false;
        }

        if (target instanceof EntityHorse && ((EntityHorse) target).isTame()) {
            return false;
        }

        if (target instanceof LOTREntityNPC) {
            LOTREntityNPC targetNPC = (LOTREntityNPC) target;

            if (targetNPC.hiredNPCInfo.isActive) {
                return false;
            }

            if (driver instanceof LOTREntityNPC) {
                try {
                    LOTRFaction driverFaction = LOTRMod.getNPCFaction((LOTREntityNPC) driver);
                    LOTRFaction targetFaction = LOTRMod.getNPCFaction(targetNPC);

                    if (retaliationThreat) {
                        return true;
                    }

                    if (driverFaction != null && targetFaction != null && !driverFaction.isBadRelation(targetFaction)) {
                        return false;
                    }
                } catch (Exception e) {
                    /*
                     * If faction reflection fails, fall through to LOTRMod.canNPCAttackEntity below.
                     */
                }
            } else {
                try {
                    LOTRFaction targetFaction = LOTRMod.getNPCFaction(targetNPC);
                    return targetFaction != null && LOTRFaction.NEAR_HARAD.isBadRelation(targetFaction);
                } catch (Exception e) {
                    return false;
                }
            }
        }

        if (driver instanceof EntityCreature) {
            return retaliationThreat
                    || LOTRMod.canNPCAttackEntity(
                    (EntityCreature) driver,
                    target,
                    false
            );
        }

        return retaliationThreat || target == mumakil.getAttackTarget();
    }

    private static boolean isTooHighForDrivenMumakilMelee(LOTREntityMumakil mumakil, EntityLivingBase target) {
        if (MumakilPerformanceTracker.isEnabled()) {
            MumakilPerformanceTracker.recordUnreachableCheck(mumakil);
        }

        if (mumakil == null || target == null) {
            return false;
        }

        double yDiff = target.posY - mumakil.posY;

        if (yDiff <= DRIVER_TARGET_MAX_Y_ABOVE_MUMAKIL) {
            return false;
        }

        double directReach = APPROACH_STOP_RANGE + DRIVER_TARGET_REACHABLE_EXTRA_RANGE;
        double directReachSq = directReach * directReach;

        return mumakil.getDistanceSqToEntity(target) > directReachSq;
    }

}
