package com.enovak.lotrmoremobs.handler;

import com.enovak.lotrmoremobs.entity.animal.LOTREntityMumakil;
import com.enovak.lotrmoremobs.entity.animal.MumakilFormationOrigin;
import com.enovak.lotrmoremobs.entity.npc.LOTREntityMumakilHowdahArcher;
import com.enovak.lotrmoremobs.config.MumakilConfig;
import com.enovak.lotrmoremobs.spawning.MumakilInvasionFormationRegistry;
import com.enovak.lotrmoremobs.spawning.MumakilWarFormationFactory;
import com.enovak.lotrmoremobs.util.MumakilPerformanceTracker;
import com.enovak.lotrmoremobs.util.MumakilServerPerformanceDiagnostics;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lotr.common.LOTRMod;
import lotr.common.entity.ai.LOTRNPCTargetSelector;
import lotr.common.entity.npc.LOTREntityNPC;
import lotr.common.fac.LOTRFaction;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;

/**
 * Safe first-pass passive howdah archers for hired Mumakil.
 *
 * Minecraft 1.7.10 only supports one normal riddenByEntity per mount. The
 * Southron Champion driver uses that real rider slot, so the howdah archers are
 * custom passive passenger entities tagged to a Mumakil and slot. The custom
 * archer entity handles no-gravity attachment on both server and client.
 *
 * Combat is deliberately lightweight: one slow target scan per Mumakil, no pathing,
 * no chase AI, and fixed passengers decide only whether to look/fire.
 */
public class MumakilHowdahArcherEventHandler {
    private static final String MUMAKIL_ARCHER_CARRIER_KEY = "LOTRMoreMobsHiredHowdahArcherCarrier";
    private static final String MUMAKIL_ARCHER_CARRIER_VERSION_KEY = "LOTRMoreMobsHiredHowdahArcherCarrierVersion";
    private static final String MUMAKIL_ARCHERS_SPAWNED_KEY = "LOTRMoreMobsHowdahArchersSpawned";
    private static final String MUMAKIL_ARCHERS_NEXT_CHECK_KEY = "LOTRMoreMobsHowdahArchersNextCheck";
    private static final String MUMAKIL_ARCHERS_NEXT_REPAIR_KEY = "LOTRMoreMobsHowdahArchersNextRepair";
    private static final String MUMAKIL_ARCHERS_NEXT_TARGET_SCAN_KEY = "LOTRMoreMobsHowdahArchersNextTargetScan";
    private static final String ARCHER_TAG_KEY = "LOTRMoreMobsHowdahArcher";
    private static final String ARCHER_SLOT_KEY = "LOTRMoreMobsHowdahArcherSlot";
    private static final String LEGACY_ARCHER_SLOT_KEY = "LOTRMoreMobsHowdahSlot";
    private static final String ARCHER_MOUNT_ID_KEY = "LOTRMoreMobsHowdahMountId";
    private static final String ARCHER_MOUNT_UUID_KEY = "LOTRMoreMobsHowdahMountUuid";
    private static final String DEAD_ARCHER_SLOTS_KEY = "LOTRMoreMobsDeadHowdahArcherSlots";
    private static final String ARCHER_SLOT_UUID_KEY_PREFIX =
            "LOTRMoreMobsHowdahArcherUuid";
    private static final String DETACHED_LANDING_WINDOW_KEY =
            "LOTRMoreMobsDetachedArcherLandingTicks";

    private static final int HOWDAH_ARCHER_COUNT = LOTREntityMumakilHowdahArcher.getHowdahArcherSlotCount();
    private static final int CURRENT_CARRIER_VERSION = 2;
    private static final int ARCHER_COUNT_CHECK_INTERVAL = 200;
    private static final int ARCHER_HEALTHY_COUNT_CHECK_INTERVAL = 600;
    private static final int ARCHER_REPAIR_COOLDOWN = 400;
    private static final int TARGET_SCAN_MIN_INTERVAL = 40;
    private static final int TARGET_SCAN_RANDOM_INTERVAL = 21;
    private static final double TARGET_SCAN_RANGE = 36.0D;
    private static final double TARGET_SCAN_VERTICAL_RANGE = 28.0D;
    private static final double ARCHER_LOCAL_LOOKUP_HORIZONTAL = 14.0D;
    private static final double ARCHER_LOCAL_LOOKUP_VERTICAL = 26.0D;
    private static final int DETACHED_LANDING_WINDOW_TICKS = 80;
    private static final double DETACHED_LANDING_STEP = 0.125D;
    private static final double DETACHED_LANDING_MAX_RAISE = 1.5D;
    private static final double DETACHED_LANDING_SUPPORT_DEPTH =
            DETACHED_LANDING_STEP + 0.05D;
    private static final boolean DEBUG_RELOAD_RECOVERY = false;

    public static void logReloadRecovery(String message) {
        if (DEBUG_RELOAD_RECOVERY) {
            System.out.println(
                    "[LOTRMoreMobs][HowdahReload] " + message
            );
        }
    }

    public static boolean isMarkedHowdahArcherCarrier(
            LOTREntityMumakil mumakil
    ) {
        if (mumakil == null) {
            return false;
        }
        NBTTagCompound data = mumakil.getEntityData();
        return data.getBoolean(MUMAKIL_ARCHER_CARRIER_KEY)
                && data.getInteger(MUMAKIL_ARCHER_CARRIER_VERSION_KEY)
                == CURRENT_CARRIER_VERSION;
    }

    public static void logLoadedMumakilHowdahState(
            LOTREntityMumakil mumakil
    ) {
        if (!DEBUG_RELOAD_RECOVERY || mumakil == null) {
            return;
        }

        logReloadRecovery(
                "Mumak loaded with howdah state"
                        + " mumak=" + mumakil.getEntityId()
                        + " hasHowdah="
                        + mumakil.hasMumakilHowdahEquipped()
                        + " hasSaddle="
                        + mumakil.hasMumakilSaddleEquipped()
                        + " origin=" + mumakil.getFormationOrigin()
                        + " expectedLiveSlots="
                        + getExpectedLivingHowdahArcherCount(mumakil)
                        + " deadSlotCount="
                        + getDeadHowdahArcherSlotCount(mumakil)
                        + " recordedSlotUuids="
                        + getRecordedLivingHowdahArcherSlotCount(mumakil)
                        + " howdahVisibilityState="
                        + mumakil.hasMumakilSyncedHowdahEquipped()
        );
    }

    public static void markDetachedArcherLandingCorrection(
            EntityLiving archer
    ) {
        if (archer != null) {
            archer.getEntityData().setInteger(
                    DETACHED_LANDING_WINDOW_KEY,
                    DETACHED_LANDING_WINDOW_TICKS
            );
        }
    }

    public static void markHiredHowdahArcherCarrier(LOTREntityMumakil mumakil) {
        if (mumakil == null || mumakil.worldObj == null || mumakil.worldObj.isRemote) {
            return;
        }

        /*
         * Do not reset MUMAKIL_ARCHERS_SPAWNED_KEY here. Resetting it on every
         * EntityJoinWorld pass was what allowed old hired Mumakil to spawn another
         * full archer set when a world/chunk reloaded.
         */
        mumakil.getEntityData().setBoolean(MUMAKIL_ARCHER_CARRIER_KEY, true);
        mumakil.getEntityData().setInteger(MUMAKIL_ARCHER_CARRIER_VERSION_KEY, CURRENT_CARRIER_VERSION);
    }

    public static void markHowdahArcherSetComplete(
            LOTREntityMumakil mumakil
    ) {
        if (mumakil == null
                || mumakil.worldObj == null
                || mumakil.worldObj.isRemote) {
            return;
        }

        markHiredHowdahArcherCarrier(mumakil);
        long worldTime = mumakil.worldObj.getTotalWorldTime();
        NBTTagCompound data = mumakil.getEntityData();
        data.setBoolean(MUMAKIL_ARCHERS_SPAWNED_KEY, true);
        data.setLong(
                MUMAKIL_ARCHERS_NEXT_CHECK_KEY,
                worldTime + ARCHER_HEALTHY_COUNT_CHECK_INTERVAL
        );
        data.setLong(
                MUMAKIL_ARCHERS_NEXT_REPAIR_KEY,
                worldTime + ARCHER_REPAIR_COOLDOWN
        );
        data.setLong(MUMAKIL_ARCHERS_NEXT_TARGET_SCAN_KEY, 0L);
    }

    /**
     * Dead-slot persistence is the authoritative, allocation-free formation
     * roster. Attached archers share the Mumak's chunk, and every passenger
     * death marks its slot before the combat AI evaluates its next tick.
     */
    public static boolean hasAnyLivingAttachedArcherSlot(
            LOTREntityMumakil mumakil
    ) {
        if (mumakil == null
                || !mumakil.hasMumakilHowdahEquipped()) {
            return false;
        }
        int livingSlotMask = (1 << HOWDAH_ARCHER_COUNT) - 1;
        int deadSlotMask = mumakil.getEntityData().getInteger(
                DEAD_ARCHER_SLOTS_KEY
        );
        return (deadSlotMask & livingSlotMask) != livingSlotMask;
    }

    /**
     * Resolves only the exact, current parent of a canonically attached archer.
     * This intentionally performs no proximity, faction, nearby-UUID, rider,
     * or shared-slot inference.
     */
    public static LOTREntityMumakil
    getFullyValidatedAttachedArcherParent(
            LOTREntityMumakilHowdahArcher archer
    ) {
        if (archer == null
                || archer.worldObj == null
                || archer.worldObj.isRemote
                || !archer.hasActiveHowdahAttachment()) {
            return null;
        }

        int slot = archer.getHowdahSlot();
        int mountEntityId = archer.getHowdahMountEntityId();
        String mountUuid = archer.getHowdahMountUuid();
        if (slot < 0
                || slot >= HOWDAH_ARCHER_COUNT
                || mountEntityId == 0
                || mountUuid.length() == 0) {
            return null;
        }

        Entity exactParent =
                archer.worldObj.getEntityByID(mountEntityId);
        if (!(exactParent instanceof LOTREntityMumakil)) {
            return null;
        }

        LOTREntityMumakil mumakil =
                (LOTREntityMumakil)exactParent;
        if (mumakil.worldObj != archer.worldObj
                || !mumakil.isEntityAlive()
                || !mumakil.hasMumakilHowdahEquipped()
                || !mountUuid.equals(
                getEntityPersistentIdString(mumakil)
        )
                || isHowdahArcherSlotDead(mumakil, slot)) {
            return null;
        }

        NBTTagCompound data = archer.getEntityData();
        if (!data.getBoolean(ARCHER_TAG_KEY)
                || data.getInteger(ARCHER_SLOT_KEY) != slot
                || data.getInteger(ARCHER_MOUNT_ID_KEY)
                != mountEntityId
                || !mountUuid.equals(
                data.getString(ARCHER_MOUNT_UUID_KEY)
        )) {
            return null;
        }

        String recordedArcherUuid =
                getRecordedHowdahArcherUuid(mumakil, slot);
        if (recordedArcherUuid.length() == 0
                || !recordedArcherUuid.equals(
                getEntityPersistentIdString(archer)
        )) {
            return null;
        }

        return mumakil;
    }

    @SubscribeEvent
    public void onEntityJoinWorld(EntityJoinWorldEvent event) {
        if (event == null || event.world == null || event.world.isRemote) {
            return;
        }

        if (event.entity instanceof LOTREntityMumakilHowdahArcher) {
            LOTREntityMumakilHowdahArcher archer = (LOTREntityMumakilHowdahArcher)event.entity;
            if (archer.isHowdahRecoveryPending()) {
                /*
                 * NBT-loaded archers deliberately join with runtime attachment
                 * inactive. Their UUID/slot recovery runs inside the entity's
                 * bounded update path.
                 */
                return;
            }
            if (!archer.isRuntimeHowdahPassenger()) {
                archer.setDead();
                return;
            }

            /*
             * A loaded passenger is deliberately nonpersistent until its
             * saved parent UUID and slot have been validated. Attachment
             * recovery reasserts the cap exemption after that validation.
             */
            makeArcherPassive(archer);
            return;
        }

        if (isTaggedHowdahArcher(event.entity) && event.entity instanceof EntityLiving) {
            ((EntityLiving)event.entity).setDead();
        }
    }

    @SubscribeEvent
    public void onLivingUpdate(LivingEvent.LivingUpdateEvent event) {
        if (event == null || event.entityLiving == null || event.entityLiving.worldObj == null) {
            return;
        }

        if (event.entityLiving.worldObj.isRemote) {
            if (event.entityLiving instanceof LOTREntityMumakil
                    && ((LOTREntityMumakil)event.entityLiving).hasMumakilHowdahEquipped()) {
                updateHowdahArcherTargets(
                        (LOTREntityMumakil)event.entityLiving,
                        event.entityLiving.worldObj.getTotalWorldTime()
                );
            }

            return;
        }

        EntityLivingBase living = event.entityLiving;

        if (living instanceof EntityLiving
                && updateDetachedArcherLanding((EntityLiving)living)) {
            return;
        }

        if (living instanceof LOTREntityMumakil) {
            LOTREntityMumakil mumakil = (LOTREntityMumakil)living;
            long serverTimingStart =
                    MumakilServerPerformanceDiagnostics.startTimer(
                            mumakil.worldObj
                    );
            boolean trackPerformance =
                    MumakilPerformanceTracker.isEnabled();
            long perfStart = trackPerformance
                    ? MumakilPerformanceTracker.startTimer()
                    : 0L;

            try {
                updateHiredMumakilArchers(mumakil);
            } finally {
                MumakilServerPerformanceDiagnostics
                        .recordRosterMaintenance(
                                mumakil.worldObj,
                                System.nanoTime()
                                        - serverTimingStart
                        );
                if (trackPerformance) {
                    MumakilPerformanceTracker.recordArcherHandler(
                            mumakil,
                            System.nanoTime() - perfStart
                    );
                }
            }
            return;
        }

        if (living instanceof LOTREntityMumakilHowdahArcher
                && !((LOTREntityMumakilHowdahArcher)living)
                .isRuntimeHowdahPassenger()
                && !((LOTREntityMumakilHowdahArcher)living)
                .isHowdahRecoveryPending()) {
            LOTREntityMumakilHowdahArcher archer =
                    (LOTREntityMumakilHowdahArcher)living;
            if (!archer.isDetachedFromDeadMumakil()) {
                living.setDead();
                return;
            }
        }

        if (living instanceof EntityLiving && isTaggedHowdahArcher(living)) {
            /*
             * Any old generic archer entities left over from prior test builds are
             * cleaned up. New passengers use LOTREntityMumakilHowdahArcher, which
             * attaches itself and disables gravity on both server and client.
             */
            if (!(living instanceof LOTREntityMumakilHowdahArcher)) {
                living.setDead();
            }
        }
    }

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        if (event == null
                || event.entityLiving == null
                || event.entityLiving.worldObj == null
                || event.entityLiving.worldObj.isRemote) {
            return;
        }

        if (event.entityLiving instanceof LOTREntityMumakilHowdahArcher) {
            markDeadHowdahArcherSlot((LOTREntityMumakilHowdahArcher)event.entityLiving);
            return;
        }

        if (!(event.entityLiving instanceof LOTREntityMumakil)) {
            return;
        }

        detachHowdahArchersForDeadMumakil((LOTREntityMumakil)event.entityLiving);
    }

    private static void detachHowdahArchersForDeadMumakil(LOTREntityMumakil mumakil) {
        List loaded = mumakil.worldObj.loadedEntityList;

        for (int i = 0; i < loaded.size(); ++i) {
            Object object = loaded.get(i);
            if (!(object instanceof LOTREntityMumakilHowdahArcher)) {
                continue;
            }

            LOTREntityMumakilHowdahArcher archer = (LOTREntityMumakilHowdahArcher)object;
            if (!archer.isDead
                    && (archer.hasActiveHowdahAttachment()
                    || archer.isHowdahRecoveryPending())
                    && isArcherAssignedToMount(archer, mumakil)) {
                archer.detachFromHowdahForMumakilDeath(mumakil);
            }
        }
    }

    private static void updateHiredMumakilArchers(LOTREntityMumakil mumakil) {
        NBTTagCompound data = mumakil.getEntityData();

        if (!isMarkedHowdahArcherCarrier(mumakil)) {
            return;
        }

        if (!mumakil.hasMumakilHowdahEquipped()) {
            return;
        }

        /*
         * Wait a few ticks so the normal LOTR hired-unit spawn path has time to
         * attach the Southron Champion driver and the existing hired-mount handler
         * has time to finish saddle/howdah sync.
         */
        if (mumakil.ticksExisted < 5) {
            return;
        }

        /*
         * Chunk NBT does not promise parent/passenger join order. Give every
         * saved passenger a complete UUID-recovery window before treating an
         * unobserved living slot as repairable.
         */
        if (mumakil.tickHowdahRosterLoadGrace()) {
            return;
        }

        long worldTime = mumakil.worldObj.getTotalWorldTime();
        updateHowdahArcherTargets(mumakil, worldTime);

        if (!mumakil.getBelongsToNPC()) {
            return;
        }

        if (!data.getBoolean(MUMAKIL_ARCHERS_SPAWNED_KEY)) {
            SlotScanResult result = normalizeExistingArchersForMumakil(mumakil);
            int spawned = spawnMissingHowdahArchers(mumakil, result.seenSlots, true);

            data.setBoolean(MUMAKIL_ARCHERS_SPAWNED_KEY, true);
            data.setLong(
                    MUMAKIL_ARCHERS_NEXT_CHECK_KEY,
                    worldTime
                            + ARCHER_HEALTHY_COUNT_CHECK_INTERVAL
            );
            data.setLong(MUMAKIL_ARCHERS_NEXT_REPAIR_KEY, worldTime + ARCHER_REPAIR_COOLDOWN);
            data.setLong(MUMAKIL_ARCHERS_NEXT_TARGET_SCAN_KEY, 0L);

            System.out.println("[LOTRMoreMobs] Initial Mumakil howdah archer set for mount="
                    + mumakil.getEntityId()
                    + ": kept="
                    + result.valid
                    + ", spawned="
                    + spawned
                    + ".");
            return;
        }

        long nextCheck = data.getLong(MUMAKIL_ARCHERS_NEXT_CHECK_KEY);
        if (nextCheck > worldTime) {
            return;
        }

        SlotScanResult result = normalizeExistingArchersForMumakil(mumakil);
        int expectedLiving =
                getExpectedLivingHowdahArcherCount(mumakil);
        int recordedLiving =
                getRecordedLivingHowdahArcherSlotCount(mumakil);

        if (result.valid >= expectedLiving) {
            data.setLong(
                    MUMAKIL_ARCHERS_NEXT_CHECK_KEY,
                    worldTime
                            + ARCHER_HEALTHY_COUNT_CHECK_INTERVAL
            );
            data.setLong(MUMAKIL_ARCHERS_NEXT_REPAIR_KEY, 0L);
            return;
        }

        if (recordedLiving >= expectedLiving
                && !isRosterNeighborhoodFullyLoaded(mumakil)) {
            /*
             * At least one save-stable living slot belongs to a chunk that
             * is not loaded. Keep its UUID authoritative and retry later;
             * spawning here would duplicate it when that chunk returns.
             */
            data.setLong(
                    MUMAKIL_ARCHERS_NEXT_CHECK_KEY,
                    worldTime + ARCHER_COUNT_CHECK_INTERVAL
            );
            return;
        }

        if (recordedLiving >= expectedLiving) {
            clearConfirmedMissingArcherUuids(
                    mumakil,
                    result.seenSlots
            );
        }

        data.setLong(
                MUMAKIL_ARCHERS_NEXT_CHECK_KEY,
                worldTime + ARCHER_COUNT_CHECK_INTERVAL
        );
        long nextRepair = data.getLong(MUMAKIL_ARCHERS_NEXT_REPAIR_KEY);
        if (nextRepair <= worldTime) {
            spawnMissingHowdahArchers(mumakil, result.seenSlots, false);
            data.setLong(MUMAKIL_ARCHERS_NEXT_REPAIR_KEY, worldTime + ARCHER_REPAIR_COOLDOWN);
        }
    }

    private static void updateHowdahArcherTargets(LOTREntityMumakil mumakil, long worldTime) {
        if (MumakilPerformanceTracker.DEBUG_DISABLE_HOWDAH_ARCHER_COMBAT) {
            clearAssignedHowdahTargets(mumakil);
            return;
        }

        NBTTagCompound data = mumakil.getEntityData();
        long nextScan = data.getLong(MUMAKIL_ARCHERS_NEXT_TARGET_SCAN_KEY);
        if (nextScan > worldTime) {
            return;
        }

        data.setLong(
                MUMAKIL_ARCHERS_NEXT_TARGET_SCAN_KEY,
                worldTime + TARGET_SCAN_MIN_INTERVAL + mumakil.worldObj.rand.nextInt(TARGET_SCAN_RANDOM_INTERVAL)
        );

        assignIndependentHowdahArcherTargets(mumakil);
    }

    private static void assignIndependentHowdahArcherTargets(LOTREntityMumakil mumakil) {
        long serverTimingStart =
                MumakilServerPerformanceDiagnostics.startTimer(
                        mumakil.worldObj
                );
        boolean trackPerformance =
                !mumakil.worldObj.isRemote
                        && MumakilPerformanceTracker.isEnabled();
        long perfStart = trackPerformance
                ? MumakilPerformanceTracker.startTimer()
                : 0L;
        List nearby = mumakil.worldObj.getEntitiesWithinAABB(
                EntityLivingBase.class,
                mumakil.boundingBox.expand(TARGET_SCAN_RANGE, TARGET_SCAN_VERTICAL_RANGE, TARGET_SCAN_RANGE)
        );
        List archers = findAttachedHowdahArchers(mumakil);
        if (archers.isEmpty()) {
            if (trackPerformance) {
                MumakilPerformanceTracker.recordArcherTargetScan(
                        mumakil,
                        nearby.size(),
                        System.nanoTime() - perfStart
                );
            }
            if (!mumakil.worldObj.isRemote) {
                MumakilServerPerformanceDiagnostics
                        .recordArcherTargetScan(
                                mumakil.worldObj,
                                System.nanoTime()
                                        - serverTimingStart
                        );
            }
            return;
        }

        List validCandidates = new ArrayList();

        for (int i = 0; i < nearby.size(); ++i) {
            EntityLivingBase candidate = (EntityLivingBase)nearby.get(i);
            if (canHowdahArchersTarget(mumakil, candidate)) {
                validCandidates.add(candidate);
            }
        }

        boolean targetChanged = false;
        for (int i = 0; i < archers.size(); ++i) {
            LOTREntityMumakilHowdahArcher archer =
                    (LOTREntityMumakilHowdahArcher)archers.get(i);
            EntityLivingBase target = findBestTargetForArcher(
                    mumakil,
                    archer,
                    validCandidates
            );
            if (archer.setAssignedHowdahTarget(target)) {
                targetChanged = true;
            }
        }

        if (targetChanged && trackPerformance) {
            MumakilPerformanceTracker.recordArcherTargetChange(mumakil);
        }

        if (trackPerformance) {
            MumakilPerformanceTracker.recordArcherTargetScan(mumakil, nearby.size(), System.nanoTime() - perfStart);
        }
        if (!mumakil.worldObj.isRemote) {
            MumakilServerPerformanceDiagnostics
                    .recordArcherTargetScan(
                            mumakil.worldObj,
                            System.nanoTime() - serverTimingStart
                    );
        }
    }

    private static boolean updateDetachedArcherLanding(EntityLiving archer) {
        NBTTagCompound data = archer.getEntityData();
        int remaining = data.getInteger(DETACHED_LANDING_WINDOW_KEY);
        if (remaining <= 0) {
            return false;
        }

        if (!archer.onGround && !archer.isCollidedVertically) {
            data.setInteger(DETACHED_LANDING_WINDOW_KEY, remaining - 1);
            return true;
        }

        List blockCollisions = new ArrayList();
        if (!hasSolidBlockCollision(
                archer,
                archer.boundingBox,
                blockCollisions
        )) {
            data.removeTag(DETACHED_LANDING_WINDOW_KEY);
            return true;
        }

        double originalX = archer.posX;
        double originalY = archer.posY;
        double originalZ = archer.posZ;

        for (double raise = DETACHED_LANDING_STEP;
             raise <= DETACHED_LANDING_MAX_RAISE + 1.0E-7D;
             raise += DETACHED_LANDING_STEP) {
            AxisAlignedBB raisedBox = AxisAlignedBB.getBoundingBox(
                    archer.boundingBox.minX,
                    archer.boundingBox.minY + raise,
                    archer.boundingBox.minZ,
                    archer.boundingBox.maxX,
                    archer.boundingBox.maxY + raise,
                    archer.boundingBox.maxZ
            );
            if (hasSolidBlockCollision(
                    archer,
                    raisedBox,
                    blockCollisions
            )) {
                continue;
            }

            AxisAlignedBB supportBox = AxisAlignedBB.getBoundingBox(
                    raisedBox.minX,
                    raisedBox.minY - DETACHED_LANDING_SUPPORT_DEPTH,
                    raisedBox.minZ,
                    raisedBox.maxX,
                    raisedBox.minY,
                    raisedBox.maxZ
            );
            if (!hasSolidBlockCollision(
                    archer,
                    supportBox,
                    blockCollisions
            )) {
                continue;
            }

            archer.setPosition(originalX, originalY + raise, originalZ);
            archer.prevPosX = originalX;
            archer.prevPosY = archer.posY;
            archer.prevPosZ = originalZ;
            archer.lastTickPosX = originalX;
            archer.lastTickPosY = archer.posY;
            archer.lastTickPosZ = originalZ;
            archer.motionY = 0.0D;
            archer.onGround = true;
            archer.isAirBorne = false;
            archer.velocityChanged = true;
            data.removeTag(DETACHED_LANDING_WINDOW_KEY);
            return true;
        }

        if (remaining <= 1) {
            data.removeTag(DETACHED_LANDING_WINDOW_KEY);
        } else {
            data.setInteger(DETACHED_LANDING_WINDOW_KEY, remaining - 1);
        }
        return true;
    }

    private static boolean hasSolidBlockCollision(
            EntityLiving entity,
            AxisAlignedBB box,
            List collisions
    ) {
        collisions.clear();
        int minX = MathHelper.floor_double(box.minX);
        int maxX = MathHelper.floor_double(box.maxX + 1.0D);
        int minY = MathHelper.floor_double(box.minY);
        int maxY = MathHelper.floor_double(box.maxY + 1.0D);
        int minZ = MathHelper.floor_double(box.minZ);
        int maxZ = MathHelper.floor_double(box.maxZ + 1.0D);

        for (int x = minX; x < maxX; ++x) {
            for (int z = minZ; z < maxZ; ++z) {
                if (!entity.worldObj.blockExists(x, minY, z)) {
                    continue;
                }
                for (int y = minY; y < maxY; ++y) {
                    Block block = entity.worldObj.getBlock(x, y, z);
                    block.addCollisionBoxesToList(
                            entity.worldObj,
                            x,
                            y,
                            z,
                            box,
                            collisions,
                            entity
                    );
                    if (!collisions.isEmpty()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static EntityLivingBase findBestTargetForArcher(
            LOTREntityMumakil mumakil,
            LOTREntityMumakilHowdahArcher archer,
            List candidates
    ) {
        LOTRNPCTargetSelector targetSelector = new LOTRNPCTargetSelector(archer);
        EntityLivingBase currentTarget = archer.getAssignedHowdahTarget();
        if (currentTarget != null
                && candidates.contains(currentTarget)
                && canSpecificArcherTarget(archer, currentTarget, targetSelector)
                && !shotPassesThroughMumak(mumakil, archer, currentTarget)) {
            return currentTarget;
        }

        EntityLivingBase bestClearTarget = null;
        EntityLivingBase bestBlockedTarget = null;
        int bestClearPriority = Integer.MAX_VALUE;
        int bestBlockedPriority = Integer.MAX_VALUE;
        double bestClearDistanceSq = Double.MAX_VALUE;
        double bestBlockedDistanceSq = Double.MAX_VALUE;

        for (int i = 0; i < candidates.size(); ++i) {
            EntityLivingBase candidate = (EntityLivingBase)candidates.get(i);
            if (!canSpecificArcherTarget(archer, candidate, targetSelector)) {
                continue;
            }

            int priority = getHowdahTargetPriority(candidate);
            double distanceSq = archer.getDistanceSqToEntity(candidate);
            boolean blockedByMumak = shotPassesThroughMumak(mumakil, archer, candidate);

            if (blockedByMumak) {
                if (priority < bestBlockedPriority
                        || priority == bestBlockedPriority && distanceSq < bestBlockedDistanceSq) {
                    bestBlockedTarget = candidate;
                    bestBlockedPriority = priority;
                    bestBlockedDistanceSq = distanceSq;
                }
            } else if (priority < bestClearPriority
                    || priority == bestClearPriority && distanceSq < bestClearDistanceSq) {
                bestClearTarget = candidate;
                bestClearPriority = priority;
                bestClearDistanceSq = distanceSq;
            }
        }

        return bestClearTarget != null ? bestClearTarget : bestBlockedTarget;
    }

    private static boolean canSpecificArcherTarget(
            LOTREntityMumakilHowdahArcher archer,
            EntityLivingBase target,
            LOTRNPCTargetSelector targetSelector
    ) {
        return targetSelector.isEntityApplicable(target)
                && LOTRMod.canNPCAttackEntity(archer, target, false);
    }

    private static boolean shotPassesThroughMumak(
            LOTREntityMumakil mumakil,
            LOTREntityMumakilHowdahArcher archer,
            EntityLivingBase target
    ) {
        Vec3 origin = Vec3.createVectorHelper(
                archer.posX,
                archer.posY + (double)archer.getEyeHeight(),
                archer.posZ
        );
        Vec3 targetPoint = Vec3.createVectorHelper(
                target.posX,
                target.posY + (double)target.getEyeHeight() * 0.75D,
                target.posZ
        );
        AxisAlignedBB mumakBody = mumakil.boundingBox.expand(0.35D, 0.35D, 0.35D);
        return mumakBody.calculateIntercept(origin, targetPoint) != null;
    }

    private static List findAttachedHowdahArchers(LOTREntityMumakil mumakil) {
        List archers = new ArrayList(HOWDAH_ARCHER_COUNT);
        List nearby = getLocalArcherCandidates(mumakil);
        for (int i = 0; i < nearby.size(); ++i) {
            Object object = nearby.get(i);
            if (!(object instanceof LOTREntityMumakilHowdahArcher)) {
                continue;
            }

            LOTREntityMumakilHowdahArcher archer = (LOTREntityMumakilHowdahArcher)object;
            if (!archer.isDead
                    && archer.hasActiveHowdahAttachment()
                    && isArcherAssignedToMount(archer, mumakil)) {
                archers.add(archer);
            }
        }

        return archers;
    }

    private static List getLocalArcherCandidates(
            LOTREntityMumakil mumakil
    ) {
        return mumakil.worldObj.getEntitiesWithinAABB(
                EntityLiving.class,
                mumakil.boundingBox.expand(
                        ARCHER_LOCAL_LOOKUP_HORIZONTAL,
                        ARCHER_LOCAL_LOOKUP_VERTICAL,
                        ARCHER_LOCAL_LOOKUP_HORIZONTAL
                )
        );
    }

    private static boolean isRosterNeighborhoodFullyLoaded(
            LOTREntityMumakil mumakil
    ) {
        if (mumakil == null
                || mumakil.worldObj == null) {
            return false;
        }

        int minimumChunkX = MathHelper.floor_double(
                mumakil.posX - ARCHER_LOCAL_LOOKUP_HORIZONTAL
        ) >> 4;
        int maximumChunkX = MathHelper.floor_double(
                mumakil.posX + ARCHER_LOCAL_LOOKUP_HORIZONTAL
        ) >> 4;
        int minimumChunkZ = MathHelper.floor_double(
                mumakil.posZ - ARCHER_LOCAL_LOOKUP_HORIZONTAL
        ) >> 4;
        int maximumChunkZ = MathHelper.floor_double(
                mumakil.posZ + ARCHER_LOCAL_LOOKUP_HORIZONTAL
        ) >> 4;

        for (int chunkX = minimumChunkX;
             chunkX <= maximumChunkX;
             ++chunkX) {
            for (int chunkZ = minimumChunkZ;
                 chunkZ <= maximumChunkZ;
                 ++chunkZ) {
                if (!mumakil.worldObj.getChunkProvider().chunkExists(
                        chunkX,
                        chunkZ
                )) {
                    return false;
                }
            }
        }
        return true;
    }

    private static void clearConfirmedMissingArcherUuids(
            LOTREntityMumakil mumakil,
            boolean[] seenSlots
    ) {
        int cleared = 0;
        for (int slot = 0; slot < HOWDAH_ARCHER_COUNT; ++slot) {
            if (seenSlots[slot]
                    || isHowdahArcherSlotDead(mumakil, slot)
                    || getRecordedHowdahArcherUuid(
                    mumakil,
                    slot
            ).length() == 0) {
                continue;
            }
            mumakil.getEntityData().setString(
                    getArcherSlotUuidKey(slot),
                    ""
            );
            ++cleared;
        }

        if (cleared > 0) {
            logReloadRecovery(
                    "Confirmed genuinely missing living slots"
                            + " mumak=" + mumakil.getEntityId()
                            + " clearedSlotUuids=" + cleared
            );
        }
    }

    public static int getExpectedLivingHowdahArcherCount(
            LOTREntityMumakil mumakil
    ) {
        if (mumakil == null) {
            return 0;
        }
        int livingSlotMask = (1 << HOWDAH_ARCHER_COUNT) - 1;
        int deadSlots = mumakil.getEntityData().getInteger(
                DEAD_ARCHER_SLOTS_KEY
        ) & livingSlotMask;
        return HOWDAH_ARCHER_COUNT - Integer.bitCount(deadSlots);
    }

    public static int getDeadHowdahArcherSlotCount(
            LOTREntityMumakil mumakil
    ) {
        return HOWDAH_ARCHER_COUNT
                - getExpectedLivingHowdahArcherCount(mumakil);
    }

    private static int getRecordedLivingHowdahArcherSlotCount(
            LOTREntityMumakil mumakil
    ) {
        if (mumakil == null) {
            return 0;
        }

        int recorded = 0;
        for (int slot = 0; slot < HOWDAH_ARCHER_COUNT; ++slot) {
            if (!isHowdahArcherSlotDead(mumakil, slot)
                    && getRecordedHowdahArcherUuid(
                    mumakil,
                    slot
            ).length() > 0) {
                ++recorded;
            }
        }
        return recorded;
    }

    public static int getLiveAttachedHowdahArcherCount(
            LOTREntityMumakil mumakil
    ) {
        if (mumakil == null
                || mumakil.worldObj == null
                || mumakil.worldObj.isRemote) {
            return 0;
        }
        return findAttachedHowdahArchers(mumakil).size();
    }

    private static void clearAssignedHowdahTargets(LOTREntityMumakil mumakil) {
        List archers = findAttachedHowdahArchers(mumakil);
        for (int i = 0; i < archers.size(); ++i) {
            ((LOTREntityMumakilHowdahArcher)archers.get(i)).setAssignedHowdahTarget(null);
        }
    }

    private static boolean canHowdahArchersTarget(LOTREntityMumakil mumakil, EntityLivingBase target) {
        if (MumakilPerformanceTracker.isEnabled()) {
            MumakilPerformanceTracker.recordArcherCandidateCheck(mumakil);
        }

        if (target == null
                || target == mumakil
                || target == mumakil.riddenByEntity
                || target instanceof LOTREntityMumakil
                || target instanceof LOTREntityMumakilHowdahArcher
                || !target.isEntityAlive()
                || target.riddenByEntity != null) {
            return false;
        }

        if (target instanceof EntityPlayer) {
            return !((EntityPlayer)target).capabilities.isCreativeMode;
        }

        if (!(target instanceof LOTREntityNPC)) {
            return false;
        }

        LOTREntityNPC npc = (LOTREntityNPC)target;
        if (npc.hiredNPCInfo.isActive) {
            return false;
        }

        LOTRFaction targetFaction = LOTRMod.getNPCFaction(npc);
        if (targetFaction == null || !LOTRFaction.NEAR_HARAD.isBadRelation(targetFaction)) {
            return false;
        }

        if (mumakil.riddenByEntity instanceof EntityCreature
                && !LOTRMod.canNPCAttackEntity((EntityCreature)mumakil.riddenByEntity, target, false)) {
            return false;
        }

        return true;
    }

    private static int getHowdahTargetPriority(EntityLivingBase target) {
        if (target instanceof LOTREntityNPC) {
            return 0;
        }

        if (target instanceof EntityPlayer) {
            return 1;
        }

        return 2;
    }

    private static int spawnMissingHowdahArchers(LOTREntityMumakil mumakil, boolean[] seenSlots, boolean initialSpawn) {
        if (MumakilPerformanceTracker.DEBUG_DO_NOT_SPAWN_HOWDAH_ARCHERS) {
            return 0;
        }

        int spawned = 0;
        StringBuilder resultSlots = new StringBuilder();
        boolean invasionFormation = mumakil.getFormationOrigin()
                == MumakilFormationOrigin.INVASION_NEAR_HARAD;
        UUID invasionId = invasionFormation
                ? mumakil.getMumakilInvasionId()
                : null;
        List invasionBonusFactions =
                mumakil.riddenByEntity instanceof LOTREntityNPC
                        ? ((LOTREntityNPC)mumakil.riddenByEntity)
                        .killBonusFactions
                        : null;

        for (int slot = 0; slot < HOWDAH_ARCHER_COUNT; ++slot) {
            if (seenSlots[slot]
                    || isHowdahArcherSlotDead(mumakil, slot)
                    || getRecordedHowdahArcherUuid(
                    mumakil,
                    slot
            ).length() > 0) {
                continue;
            }

            LOTREntityMumakilHowdahArcher archer =
                    spawnAttachedHowdahArcher(
                    mumakil,
                    slot,
                    mumakil.getFormationOrigin()
                            == MumakilFormationOrigin.PLAYER_HIRED,
                    invasionId,
                    invasionBonusFactions
            );
            if (archer != null) {
                if (invasionFormation) {
                    archer.getEntityData().setInteger(
                            MumakilInvasionFormationRegistry
                                    .INVASION_MEMBER_WEIGHT_KEY,
                            MumakilConfig
                                    .INVASION_ARCHER_BUDGET_VALUE
                    );
                }
                seenSlots[slot] = true;
                ++spawned;

                if (!initialSpawn) {
                    resultSlots.append(resultSlots.length() == 0 ? "" : ",").append(slot);
                }
            }
        }

        if (!initialSpawn && spawned > 0) {
            System.out.println("[LOTRMoreMobs] Spawned "
                    + spawned
                    + " missing howdah archer slots for mount="
                    + mumakil.getEntityId()
                    + " slots="
                    + resultSlots
                    + ".");
        }

        return spawned;
    }

    public static LOTREntityMumakilHowdahArcher
    spawnAttachedHowdahArcher(
            LOTREntityMumakil mumakil,
            int slot,
            boolean persistent
    ) {
        return spawnAttachedHowdahArcher(
                mumakil,
                slot,
                persistent,
                null,
                null
        );
    }

    public static LOTREntityMumakilHowdahArcher
    spawnAttachedHowdahArcher(
            LOTREntityMumakil mumakil,
            int slot,
            boolean persistent,
            UUID invasionId,
            List killBonusFactions
    ) {
        if (mumakil == null
                || mumakil.worldObj == null
                || mumakil.worldObj.isRemote
                || slot < 0
                || slot >= HOWDAH_ARCHER_COUNT) {
            return null;
        }

        World world = mumakil.worldObj;
        LOTREntityMumakilHowdahArcher archer = new LOTREntityMumakilHowdahArcher(world);
        archer.onSpawnWithEgg(null);
        if (invasionId != null) {
            archer.setInvasionID(invasionId);
        }
        if (killBonusFactions != null) {
            archer.killBonusFactions.addAll(killBonusFactions);
        }
        attachExistingHowdahArcher(
                mumakil,
                archer,
                slot,
                persistent
        );

        try {
            if (world.spawnEntityInWorld(archer)) {
                return archer;
            }
            archer.setDead();
            return null;
        } catch (RuntimeException e) {
            archer.setDead();
            throw e;
        }
    }

    public static void attachExistingHowdahArcher(
            LOTREntityMumakil mumakil,
            LOTREntityMumakilHowdahArcher archer,
            int slot,
            boolean persistent
    ) {
        if (mumakil == null || archer == null) {
            return;
        }

        /*
         * Fixed runtime passengers are explicitly exempt from LOTR's
         * free-roaming NPC spawn count. The exemption follows a complete
         * runtime attachment, not merely a formation-origin marker.
         */
        if (!archer.completeValidatedHowdahAttachment(
                mumakil,
                slot
        )) {
            return;
        }
        recordHowdahArcherUuid(mumakil, archer, slot);
        archer.isNPCPersistent = persistent
                || applyRuntimePassengerSpawnCapExemption(archer);
        archer.setShouldTraderRespawn(false);
        tagArcher(
                archer,
                mumakil,
                getEntityPersistentIdString(mumakil),
                slot
        );
        makeArcherPassive(archer);
    }

    /**
     * Validates the save-stable parent UUID and slot before restoring any
     * runtime-only state. This is shared by server recovery and client spawn
     * ordering, while the authoritative roster and cap state remain
     * server-only.
     */
    public static boolean restoreLoadedHowdahArcherAttachment(
            LOTREntityMumakil mumakil,
            LOTREntityMumakilHowdahArcher archer
    ) {
        if (mumakil == null
                || archer == null
                || mumakil.worldObj == null
                || archer.worldObj != mumakil.worldObj
                || !mumakil.isEntityAlive()) {
            return false;
        }

        int slot = archer.getHowdahSlot();
        String savedMountUuid = archer.getHowdahMountUuid();
        String actualMountUuid = getEntityPersistentIdString(mumakil);
        if (slot < 0
                || slot >= HOWDAH_ARCHER_COUNT
                || savedMountUuid.length() == 0) {
            logReloadRecovery(
                    "Slot validation failed"
                            + " archer=" + archer.getEntityId()
                            + " mumak=" + mumakil.getEntityId()
                            + " slot=" + slot
                            + " parentUuid=" + savedMountUuid
            );
            if (!mumakil.worldObj.isRemote) {
                archer.setDead();
            }
            return false;
        }

        if (mumakil.worldObj.isRemote) {
            /*
             * The current-session entity tracker ID came from the server in
             * IEntityAdditionalSpawnData. Vanilla does not synchronize the
             * Mumak persistent UUID to the client entity object, so comparing
             * that client-local UUID would reject a valid parent.
             */
            if (!mumakil.hasMumakilHowdahEquipped()) {
                return false;
            }
            boolean attached =
                    archer.completeValidatedHowdahAttachment(
                            mumakil,
                            slot
                    );
            if (attached) {
                logReloadRecovery(
                        "Parent resolved; slot validation passed; canonical "
                                + "attachment completed"
                                + " archer=" + archer.getEntityId()
                                + " mumak=" + mumakil.getEntityId()
                                + " slot=" + slot
                                + " positionLockActive="
                                + archer.isHowdahPositionLockActive()
                );
            }
            return attached;
        }

        if (!savedMountUuid.equals(actualMountUuid)) {
            logReloadRecovery(
                    "Slot validation failed: Mumak UUID mismatch"
                            + " archer=" + archer.getEntityId()
                            + " mumak=" + mumakil.getEntityId()
                            + " slot=" + slot
                            + " savedParentUuid=" + savedMountUuid
                            + " actualParentUuid=" + actualMountUuid
            );
            archer.setDead();
            return false;
        }

        if (!mumakil.hasMumakilHowdahEquipped()) {
            logReloadRecovery(
                    "Removed as orphan: resolved Mumak has no howdah"
                            + " archer=" + archer.getEntityId()
                            + " mumak=" + mumakil.getEntityId()
                            + " slot=" + slot
            );
            archer.setDead();
            return false;
        }

        if (!areAttachmentChunksLoaded(mumakil, archer)) {
            logReloadRecovery(
                    "Recovery pending: attachment chunks not fully loaded"
                            + " archer=" + archer.getEntityId()
                            + " mumak=" + mumakil.getEntityId()
                            + " slot=" + slot
            );
            return false;
        }

        if (isHowdahArcherSlotDead(mumakil, slot)) {
            logReloadRecovery(
                    "Saved archer rejected for confirmed-dead slot"
                            + " archer=" + archer.getEntityId()
                            + " mumak=" + mumakil.getEntityId()
                            + " slot=" + slot
            );
            archer.setDead();
            return false;
        }

        String archerUuid = getEntityPersistentIdString(archer);
        String recordedUuid = getRecordedHowdahArcherUuid(
                mumakil,
                slot
        );
        if (recordedUuid.length() > 0
                && !recordedUuid.equals(archerUuid)) {
            logReloadRecovery(
                    "Duplicate slot rejected"
                            + " archer=" + archer.getEntityId()
                            + " mumak=" + mumakil.getEntityId()
                            + " slot=" + slot
                            + " expectedArcherUuid=" + recordedUuid
                            + " rejectedArcherUuid=" + archerUuid
            );
            archer.setDead();
            return false;
        }

        LOTREntityMumakilHowdahArcher otherOwner =
                findOtherLivingHowdahSlotOwner(
                        mumakil,
                        archer,
                        slot
                );
        if (otherOwner != null) {
            String otherUuid =
                    getEntityPersistentIdString(otherOwner);
            if (recordedUuid.equals(archerUuid)) {
                otherOwner.setDead();
                logReloadRecovery(
                        "Duplicate slot rejected"
                                + " archer=" + otherOwner.getEntityId()
                                + " mumak=" + mumakil.getEntityId()
                                + " slot=" + slot
                                + " expectedArcherUuid=" + archerUuid
                                + " rejectedArcherUuid=" + otherUuid
                );
            } else {
                archer.setDead();
                logReloadRecovery(
                        "Duplicate slot rejected"
                                + " archer=" + archer.getEntityId()
                                + " mumak=" + mumakil.getEntityId()
                                + " slot=" + slot
                                + " livingOwnerUuid=" + otherUuid
                                + " rejectedArcherUuid=" + archerUuid
                );
                return false;
            }
        }

        logReloadRecovery(
                "Parent resolved; slot validation passed"
                        + " archer=" + archer.getEntityId()
                        + " mumak=" + mumakil.getEntityId()
                        + " slot=" + slot
        );
        if (!archer.completeValidatedHowdahAttachment(
                mumakil,
                slot
        )) {
            return false;
        }
        recordHowdahArcherUuid(mumakil, archer, slot);
        tagArcher(
                archer,
                mumakil,
                actualMountUuid,
                slot
        );
        if (!applyRuntimePassengerSpawnCapExemption(archer)) {
            return false;
        }
        makeArcherPassive(archer);
        logReloadRecovery(
                "Canonical attachment completed"
                        + " archer=" + archer.getEntityId()
                        + " mumak=" + mumakil.getEntityId()
                        + " slot=" + slot
                        + " runtimeEntityId="
                        + archer.getHowdahMountEntityId()
                        + " capExempt=true"
                        + " positionLockActive="
                        + archer.isHowdahPositionLockActive()
                        + " noClip=" + archer.noClip
                        + " damageable="
                        + !archer.isEntityInvulnerable()
                        + " targetingEnabled="
                        + archer.hasActiveHowdahAttachment()
                        + " assignedTarget="
                        + (archer.getAssignedHowdahTarget() != null)
        );
        return true;
    }

    private static boolean areAttachmentChunksLoaded(
            LOTREntityMumakil mumakil,
            LOTREntityMumakilHowdahArcher archer
    ) {
        if (mumakil == null
                || archer == null
                || mumakil.worldObj == null) {
            return false;
        }
        int mumakChunkX =
                MathHelper.floor_double(mumakil.posX) >> 4;
        int mumakChunkZ =
                MathHelper.floor_double(mumakil.posZ) >> 4;
        int archerChunkX =
                MathHelper.floor_double(archer.posX) >> 4;
        int archerChunkZ =
                MathHelper.floor_double(archer.posZ) >> 4;
        return mumakil.worldObj.getChunkProvider().chunkExists(
                mumakChunkX,
                mumakChunkZ
        ) && mumakil.worldObj.getChunkProvider().chunkExists(
                archerChunkX,
                archerChunkZ
        );
    }

    private static LOTREntityMumakilHowdahArcher
    findOtherLivingHowdahSlotOwner(
            LOTREntityMumakil mumakil,
            LOTREntityMumakilHowdahArcher candidate,
            int slot
    ) {
        List nearby = getLocalArcherCandidates(mumakil);
        for (int i = 0; i < nearby.size(); ++i) {
            Object object = nearby.get(i);
            if (!(object
                    instanceof LOTREntityMumakilHowdahArcher)
                    || object == candidate) {
                continue;
            }
            LOTREntityMumakilHowdahArcher other =
                    (LOTREntityMumakilHowdahArcher)object;
            if (!other.isDead
                    && other.getHowdahSlot() == slot
                    && isArcherAssignedToMount(other, mumakil)
                    && (other.hasActiveHowdahAttachment()
                    || other.isHowdahRecoveryPending())) {
                return other;
            }
        }
        return null;
    }

    private static SlotScanResult normalizeExistingArchersForMumakil(LOTREntityMumakil mumakil) {
        SlotScanResult localResult = normalizeArcherCandidatesForMumakil(
                mumakil,
                getLocalArcherCandidates(mumakil)
        );
        if (localResult.valid
                >= getExpectedLivingHowdahArcherCount(mumakil)) {
            return localResult;
        }

        /*
         * A healthy roster never scans the world's complete entity list.
         * Fall back only when a local slot is genuinely missing so legacy or
         * temporarily misplaced passengers can still be repaired safely.
         */
        return normalizeArcherCandidatesForMumakil(
                mumakil,
                mumakil.worldObj.loadedEntityList
        );
    }

    private static SlotScanResult
    normalizeArcherCandidatesForMumakil(
            LOTREntityMumakil mumakil,
            List candidates
    ) {
        SlotScanResult result = new SlotScanResult();
        String mountUuid = getEntityPersistentIdString(mumakil);
        int invalidRemoved = 0;
        int duplicateRemoved = 0;
        int staleRemoved = 0;

        for (int i = 0; i < candidates.size(); ++i) {
            Object object = candidates.get(i);
            if (!(object instanceof EntityLiving) || !isTaggedHowdahArcher((Entity)object)) {
                continue;
            }

            EntityLiving archer = (EntityLiving)object;
            if (archer.isDead) {
                continue;
            }

            if (!isArcherAssignedToMount(archer, mumakil)) {
                continue;
            }

            int slot = getArcherSlot(archer);
            if (slot < 0 || slot >= HOWDAH_ARCHER_COUNT || !(archer instanceof LOTREntityMumakilHowdahArcher)) {
                archer.setDead();
                ++invalidRemoved;
                continue;
            }

            if (isHowdahArcherSlotDead(mumakil, slot)) {
                archer.setDead();
                ++staleRemoved;
                continue;
            }

            LOTREntityMumakilHowdahArcher howdahArcher =
                    (LOTREntityMumakilHowdahArcher)archer;
            if (howdahArcher.isRuntimeHowdahPassenger()
                    && !howdahArcher.hasActiveHowdahAttachment()) {
                /*
                 * This normally-impossible partial state must lose passenger
                 * protections before roster logic observes it.
                 */
                howdahArcher.beginHowdahAttachmentRecovery();
            }
            boolean activeAttachment =
                    howdahArcher.hasActiveHowdahAttachment();
            boolean recoveryReserved =
                    howdahArcher.isHowdahRecoveryPending()
                            && howdahArcher
                            .hasRecoverableHowdahIdentity();
            if (!activeAttachment && !recoveryReserved) {
                archer.setDead();
                ++staleRemoved;
                continue;
            }

            String archerUuid =
                    getEntityPersistentIdString(archer);
            String recordedUuid =
                    getRecordedHowdahArcherUuid(
                            mumakil,
                            slot
                    );
            if (recordedUuid.length() > 0
                    && !recordedUuid.equals(archerUuid)) {
                archer.setDead();
                ++duplicateRemoved;
                logReloadRecovery(
                        "Duplicate slot rejected during normalization"
                                + " archer=" + archer.getEntityId()
                                + " mumak=" + mumakil.getEntityId()
                                + " slot=" + slot
                                + " expectedArcherUuid="
                                + recordedUuid
                                + " rejectedArcherUuid="
                                + archerUuid
                );
                continue;
            }

            if (result.seenSlots[slot]) {
                archer.setDead();
                ++duplicateRemoved;
                continue;
            }

            result.seenSlots[slot] = true;
            if (activeAttachment) {
                ++result.valid;
                tagArcher(
                        howdahArcher,
                        mumakil,
                        mountUuid,
                        slot
                );
                recordHowdahArcherUuid(
                        mumakil,
                        howdahArcher,
                        slot
                );
                applyRuntimePassengerSpawnCapExemption(
                        howdahArcher
                );
                makeArcherPassive(archer);
            } else {
                /*
                 * The persisted UUID reserves this slot during bounded
                 * recovery, but it is intentionally not counted healthy until
                 * canonical activation has established this tick's lock.
                 */
                howdahArcher.isNPCPersistent = false;
            }
        }

        if (invalidRemoved > 0 || duplicateRemoved > 0 || staleRemoved > 0) {
            System.out.println("[LOTRMoreMobs] Cleaned passive Mumakil howdah archers for mount="
                    + mumakil.getEntityId()
                    + ": stale="
                    + staleRemoved
                    + ", invalid="
                    + invalidRemoved
                    + ", duplicate="
                    + duplicateRemoved
                    + ".");
        }

        return result;
    }

    /**
     * LOTREntityNPC#getSpawnCountValue is final in LOTR v36.15 and returns
     * zero for persistent NPCs. Fixed howdah passengers use that existing
     * native exclusion while their runtime attachment record is valid.
     * Detachment clears persistence in the passenger entity before it resumes
     * ordinary ground-NPC behavior.
     */
    private static boolean applyRuntimePassengerSpawnCapExemption(
            LOTREntityMumakilHowdahArcher archer
    ) {
        if (archer == null
                || !archer.hasActiveHowdahAttachment()) {
            return false;
        }
        int slot = archer.getHowdahSlot();
        boolean hasMountAssignment =
                archer.getHowdahMountEntityId() != 0
                        || archer.getHowdahMountUuid().length() > 0;
        if (slot < 0
                || slot >= HOWDAH_ARCHER_COUNT
                || !hasMountAssignment) {
            return false;
        }
        archer.isNPCPersistent = true;
        archer.setShouldTraderRespawn(false);
        return true;
    }

    private static boolean isArcherAssignedToMount(EntityLiving archer, LOTREntityMumakil mumakil) {
        NBTTagCompound data = archer.getEntityData();
        if (mumakil != null
                && mumakil.worldObj != null
                && mumakil.worldObj.isRemote
                && archer
                instanceof LOTREntityMumakilHowdahArcher
                && ((LOTREntityMumakilHowdahArcher)archer)
                .hasActiveHowdahAttachment()) {
            /*
             * Server persistent UUIDs are not vanilla entity-spawn data on
             * 1.7.10. Once client canonical activation has validated the
             * server-supplied tracker ID, use that current-session mapping for
             * client look/target updates.
             */
            return data.getInteger(ARCHER_MOUNT_ID_KEY)
                    == mumakil.getEntityId();
        }
        String archerMountUuid = data.getString(ARCHER_MOUNT_UUID_KEY);
        if (archerMountUuid != null
                && archerMountUuid.length() > 0) {
            return archerMountUuid.equals(
                    getEntityPersistentIdString(mumakil)
            );
        }

        /*
         * Numeric IDs are a legacy-only fallback. Once a UUID exists it is
         * authoritative, because entity IDs are reassigned on every load.
         */
        return data.getInteger(ARCHER_MOUNT_ID_KEY)
                == mumakil.getEntityId();
    }

    private static void markDeadHowdahArcherSlot(LOTREntityMumakilHowdahArcher archer) {
        if (archer == null
                || (!archer.hasActiveHowdahAttachment()
                && !archer.isHowdahRecoveryPending())
                || archer.isDetachedFromDeadMumakil()) {
            return;
        }

        int slot = archer.getHowdahSlot();
        if (slot < 0 || slot >= HOWDAH_ARCHER_COUNT || slot >= 32) {
            return;
        }

        LOTREntityMumakil mumakil = findAssignedMumakilForArcher(archer);
        if (mumakil != null) {
            markHowdahArcherSlotDead(mumakil, slot);
        }
    }

    private static LOTREntityMumakil findAssignedMumakilForArcher(LOTREntityMumakilHowdahArcher archer) {
        int mountId = archer.getHowdahMountEntityId();
        Entity entity = mountId == 0 || archer.worldObj == null ? null : archer.worldObj.getEntityByID(mountId);
        String mountUuid = archer.getHowdahMountUuid();
        if (entity instanceof LOTREntityMumakil
                && mountUuid.equals(
                getEntityPersistentIdString(entity)
        )) {
            return (LOTREntityMumakil)entity;
        }

        if (archer.worldObj == null || mountUuid == null || mountUuid.length() == 0) {
            return null;
        }

        List loaded = archer.worldObj.loadedEntityList;
        for (int i = 0; i < loaded.size(); ++i) {
            Object object = loaded.get(i);
            if (object instanceof LOTREntityMumakil
                    && mountUuid.equals(getEntityPersistentIdString((Entity)object))) {
                return (LOTREntityMumakil)object;
            }
        }

        return null;
    }

    private static boolean isHowdahArcherSlotDead(LOTREntityMumakil mumakil, int slot) {
        return slot >= 0
                && slot < 32
                && (mumakil.getEntityData().getInteger(DEAD_ARCHER_SLOTS_KEY) & (1 << slot)) != 0;
    }

    private static void markHowdahArcherSlotDead(LOTREntityMumakil mumakil, int slot) {
        mumakil.getEntityData().setInteger(
                DEAD_ARCHER_SLOTS_KEY,
                mumakil.getEntityData().getInteger(DEAD_ARCHER_SLOTS_KEY) | (1 << slot)
        );
        mumakil.getEntityData().setString(
                getArcherSlotUuidKey(slot),
                ""
        );
    }

    private static String getArcherSlotUuidKey(int slot) {
        return ARCHER_SLOT_UUID_KEY_PREFIX + slot;
    }

    private static String getRecordedHowdahArcherUuid(
            LOTREntityMumakil mumakil,
            int slot
    ) {
        if (mumakil == null
                || slot < 0
                || slot >= HOWDAH_ARCHER_COUNT) {
            return "";
        }
        return mumakil.getEntityData().getString(
                getArcherSlotUuidKey(slot)
        );
    }

    private static void recordHowdahArcherUuid(
            LOTREntityMumakil mumakil,
            LOTREntityMumakilHowdahArcher archer,
            int slot
    ) {
        if (mumakil == null
                || archer == null
                || slot < 0
                || slot >= HOWDAH_ARCHER_COUNT
                || isHowdahArcherSlotDead(mumakil, slot)) {
            return;
        }
        mumakil.getEntityData().setString(
                getArcherSlotUuidKey(slot),
                getEntityPersistentIdString(archer)
        );
    }

    private static void tagArcher(LOTREntityMumakilHowdahArcher archer, LOTREntityMumakil mumakil, String mountUuid, int slot) {
        NBTTagCompound data = archer.getEntityData();
        data.setBoolean(ARCHER_TAG_KEY, true);
        data.setInteger(ARCHER_SLOT_KEY, slot);
        data.setInteger(ARCHER_MOUNT_ID_KEY, mumakil.getEntityId());
        data.setString(ARCHER_MOUNT_UUID_KEY, mountUuid);
    }

    private static int getArcherSlot(EntityLiving archer) {
        NBTTagCompound data = archer.getEntityData();
        if (data.hasKey(ARCHER_SLOT_KEY)) {
            return data.getInteger(ARCHER_SLOT_KEY);
        }

        if (data.hasKey(LEGACY_ARCHER_SLOT_KEY)) {
            int slot = data.getInteger(LEGACY_ARCHER_SLOT_KEY);
            data.setInteger(ARCHER_SLOT_KEY, slot);
            return slot;
        }

        return -1;
    }

    private static void makeArcherPassive(EntityLiving archer) {
        if (archer.tasks != null && archer.tasks.taskEntries != null && !archer.tasks.taskEntries.isEmpty()) {
            archer.tasks.taskEntries.clear();
        }

        if (archer.targetTasks != null && archer.targetTasks.taskEntries != null && !archer.targetTasks.taskEntries.isEmpty()) {
            archer.targetTasks.taskEntries.clear();
        }

        if (archer instanceof EntityCreature) {
            EntityCreature creature = (EntityCreature)archer;
            creature.setAttackTarget(null);

            if (!(archer instanceof LOTREntityMumakilHowdahArcher) && creature.getNavigator() != null) {
                creature.getNavigator().clearPathEntity();
            }
        }

        archer.setRevengeTarget(null);
        archer.motionX = 0.0D;
        archer.motionY = 0.0D;
        archer.motionZ = 0.0D;
        archer.fallDistance = 0.0F;
        archer.onGround = true;
        archer.isAirBorne = false;

        if (!(archer instanceof LOTREntityMumakilHowdahArcher)) {
            archer.func_110163_bv();
        }
    }

    private static boolean isTaggedHowdahArcher(Entity entity) {
        return entity != null && entity.getEntityData().getBoolean(ARCHER_TAG_KEY);
    }

    private static String getEntityPersistentIdString(Entity entity) {
        return entity == null ? "" : entity.getPersistentID().toString();
    }

    private static class SlotScanResult {
        private final boolean[] seenSlots = new boolean[HOWDAH_ARCHER_COUNT];
        private int valid;
    }
}
