package com.enovak.lotrmoremobs.handler;

import com.enovak.lotrmoremobs.entity.animal.LOTREntityMumakil;
import com.enovak.lotrmoremobs.entity.npc.LOTREntityMumakilHowdahArcher;
import com.enovak.lotrmoremobs.util.MumakilServerPerformanceDiagnostics;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import java.util.List;
import lotr.common.LOTRMod;
import lotr.common.entity.npc.LOTREntityNPC;
import lotr.common.fac.LOTRFaction;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.pathfinding.PathEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraftforge.event.entity.living.LivingEvent;

/**
 * Bounded, respectful sidestep requests for Near Harad allies standing in a
 * moving formation's immediate travel corridor.
 */
public final class MumakilMakeWayEventHandler {
    public static final int MAKE_WAY_SCAN_INTERVAL_TICKS = 30;
    public static final double MAKE_WAY_CORRIDOR_LENGTH = 12.0D;
    public static final double MAKE_WAY_CORRIDOR_HALF_WIDTH = 5.0D;
    public static final int MAKE_WAY_CANDIDATE_CAP = 8;
    public static final int MAKE_WAY_ACCEPTED_MEMORY_TICKS = 80;
    public static final int MAKE_WAY_FAILED_MEMORY_TICKS = 30;
    public static final double MAKE_WAY_SIDESTEP_DISTANCE = 4.0D;

    private static final String MAKE_WAY_UNTIL_KEY =
            "LOTRMoreMobsMumakMakeWayUntil";
    private static final double MOVEMENT_THRESHOLD_SQ = 2.5E-3D;
    private static final double CORRIDOR_START = 1.0D;
    private static final double ACTIVE_COMBAT_LENGTH = 7.5D;
    private static final double ACTIVE_COMBAT_HALF_WIDTH = 3.75D;
    private static final double MAKE_WAY_PATH_SPEED = 0.9D;

    @SubscribeEvent
    public void onLivingUpdate(LivingEvent.LivingUpdateEvent event) {
        if (!(event.entityLiving instanceof LOTREntityMumakil)) {
            return;
        }

        LOTREntityMumakil mumakil =
                (LOTREntityMumakil)event.entityLiving;
        if (mumakil.worldObj == null
                || mumakil.worldObj.isRemote
                || !mumakil.isEntityAlive()
                || !mumakil.isHiredWarMumakil()
                || !mumakil.hasMumakilHowdahEquipped()
                || mumakil.riddenByEntity instanceof EntityPlayer
                || (mumakil.ticksExisted + mumakil.getEntityId())
                % MAKE_WAY_SCAN_INTERVAL_TICKS != 0) {
            return;
        }

        Vec3 direction = getTravelDirection(mumakil);
        if (direction == null) {
            return;
        }

        long timingStart =
                MumakilServerPerformanceDiagnostics.startTimer(
                        mumakil.worldObj
                );
        try {
            scanForwardCorridor(mumakil, direction);
        } finally {
            MumakilServerPerformanceDiagnostics.recordMakeWayScan(
                    mumakil.worldObj,
                    System.nanoTime() - timingStart
            );
        }
    }

    private static Vec3 getTravelDirection(LOTREntityMumakil mumakil) {
        double motionSq = mumakil.motionX * mumakil.motionX
                + mumakil.motionZ * mumakil.motionZ;
        if (motionSq < MOVEMENT_THRESHOLD_SQ) {
            return null;
        }

        PathEntity path = mumakil.getNavigator().getPath();
        if (path != null && !path.isFinished()) {
            int index = path.getCurrentPathIndex();
            int length = path.getCurrentPathLength();
            for (int i = index; i < length && i <= index + 1; ++i) {
                Vec3 point = path.getVectorFromIndex(mumakil, i);
                double dx = point.xCoord - mumakil.posX;
                double dz = point.zCoord - mumakil.posZ;
                double distanceSq = dx * dx + dz * dz;
                if (distanceSq >= 0.25D) {
                    double distance = MathHelper.sqrt_double(distanceSq);
                    return Vec3.createVectorHelper(
                            dx / distance,
                            0.0D,
                            dz / distance
                    );
                }
            }
        }

        double speed = MathHelper.sqrt_double(motionSq);
        return Vec3.createVectorHelper(
                mumakil.motionX / speed,
                0.0D,
                mumakil.motionZ / speed
        );
    }

    private static void scanForwardCorridor(
            LOTREntityMumakil mumakil,
            Vec3 direction
    ) {
        double endX = mumakil.posX
                + direction.xCoord * MAKE_WAY_CORRIDOR_LENGTH;
        double endZ = mumakil.posZ
                + direction.zCoord * MAKE_WAY_CORRIDOR_LENGTH;
        AxisAlignedBB scanBox = AxisAlignedBB.getBoundingBox(
                Math.min(mumakil.posX, endX)
                        - MAKE_WAY_CORRIDOR_HALF_WIDTH,
                mumakil.posY - 2.0D,
                Math.min(mumakil.posZ, endZ)
                        - MAKE_WAY_CORRIDOR_HALF_WIDTH,
                Math.max(mumakil.posX, endX)
                        + MAKE_WAY_CORRIDOR_HALF_WIDTH,
                mumakil.posY + 6.0D,
                Math.max(mumakil.posZ, endZ)
                        + MAKE_WAY_CORRIDOR_HALF_WIDTH
        );
        List nearby = mumakil.worldObj.getEntitiesWithinAABB(
                LOTREntityNPC.class,
                scanBox
        );
        long now = mumakil.worldObj.getTotalWorldTime();
        int attempted = 0;

        for (int i = 0;
             i < nearby.size() && attempted < MAKE_WAY_CANDIDATE_CAP;
             ++i) {
            LOTREntityNPC npc = (LOTREntityNPC)nearby.get(i);
            if (!isEligibleAlly(mumakil, npc)
                    || npc.getEntityData().getLong(MAKE_WAY_UNTIL_KEY)
                    > now) {
                continue;
            }

            double relativeX = npc.posX - mumakil.posX;
            double relativeZ = npc.posZ - mumakil.posZ;
            double forward = relativeX * direction.xCoord
                    + relativeZ * direction.zCoord;
            double lateral = relativeX * -direction.zCoord
                    + relativeZ * direction.xCoord;
            if (forward < CORRIDOR_START
                    || forward > MAKE_WAY_CORRIDOR_LENGTH
                    || Math.abs(lateral)
                    > MAKE_WAY_CORRIDOR_HALF_WIDTH) {
                continue;
            }

            if (npc.getAttackTarget() != null
                    && (forward > ACTIVE_COMBAT_LENGTH
                    || Math.abs(lateral)
                    > ACTIVE_COMBAT_HALF_WIDTH)) {
                continue;
            }

            ++attempted;
            boolean accepted = requestSidestep(
                    npc,
                    direction,
                    lateral
            );
            npc.getEntityData().setLong(
                    MAKE_WAY_UNTIL_KEY,
                    now + (accepted
                            ? MAKE_WAY_ACCEPTED_MEMORY_TICKS
                            : MAKE_WAY_FAILED_MEMORY_TICKS)
            );
        }
    }

    private static boolean isEligibleAlly(
            LOTREntityMumakil mumakil,
            LOTREntityNPC npc
    ) {
        if (npc == null
                || npc == mumakil.riddenByEntity
                || !npc.isEntityAlive()
                || npc instanceof LOTREntityMumakilHowdahArcher
                || npc.ridingEntity instanceof LOTREntityMumakil) {
            return false;
        }

        try {
            return LOTRMod.getNPCFaction(npc) == LOTRFaction.NEAR_HARAD;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static boolean requestSidestep(
            LOTREntityNPC npc,
            Vec3 direction,
            double lateral
    ) {
        EntityCreature mover = npc;
        Entity mount = npc.ridingEntity;
        if (mount instanceof EntityCreature
                && !(mount instanceof LOTREntityMumakil)) {
            mover = (EntityCreature)mount;
        }

        double sideSign;
        if (Math.abs(lateral) > 0.25D) {
            sideSign = lateral < 0.0D ? -1.0D : 1.0D;
        } else {
            sideSign = (npc.getEntityId() & 1) == 0 ? -1.0D : 1.0D;
        }

        if (trySidestep(mover, direction, sideSign)) {
            return true;
        }
        return trySidestep(mover, direction, -sideSign);
    }

    private static boolean trySidestep(
            EntityCreature mover,
            Vec3 direction,
            double sideSign
    ) {
        double sideX = -direction.zCoord * sideSign;
        double sideZ = direction.xCoord * sideSign;
        double targetX = mover.posX
                + sideX * MAKE_WAY_SIDESTEP_DISTANCE;
        double targetZ = mover.posZ
                + sideZ * MAKE_WAY_SIDESTEP_DISTANCE;
        double targetY = MathHelper.floor_double(
                mover.boundingBox.minY
        );
        return mover.getNavigator().tryMoveToXYZ(
                targetX,
                targetY,
                targetZ,
                MAKE_WAY_PATH_SPEED
        );
    }
}
