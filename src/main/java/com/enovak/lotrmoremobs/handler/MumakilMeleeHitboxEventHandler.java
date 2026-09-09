package com.enovak.lotrmoremobs.handler;

import com.enovak.lotrmoremobs.entity.animal.LOTREntityMumakil;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraftforge.event.entity.living.LivingEvent;

/**
 * Supplements normal melee AI when a creature is close enough to any edge of
 * the Mumakil's large hitbox but still too far from its center for vanilla's
 * center-distance melee check.
 *
 * MUMAKIL_EDGE_MELEE_AND_REACH_V1
 */
public final class MumakilMeleeHitboxEventHandler {
    private static final String NEXT_EDGE_MELEE_TICK_KEY =
            "LOTRMoreMobsNextMumakilEdgeMeleeTick";

    private static final int EDGE_MELEE_COOLDOWN_TICKS = 20;

    @SubscribeEvent
    public void onLivingUpdate(LivingEvent.LivingUpdateEvent event) {
        if (event == null
                || event.entityLiving == null
                || event.entityLiving.worldObj == null
                || event.entityLiving.worldObj.isRemote
                || !(event.entityLiving instanceof EntityCreature)) {
            return;
        }

        EntityCreature attacker =
                (EntityCreature)event.entityLiving;
        EntityLivingBase target = attacker.getAttackTarget();

        if (!(target instanceof LOTREntityMumakil)
                || attacker.isDead
                || !attacker.isEntityAlive()
                || target.isDead
                || !target.isEntityAlive()) {
            return;
        }

        LOTREntityMumakil mumakil =
                (LOTREntityMumakil)target;

        /*
         * Use the same general reach scale used by 1.7.10 melee AI, but
         * compare it to the nearest edges of the two bounding boxes.
         */
        double reachSq = getNormalMeleeReachSq(
                attacker,
                mumakil
        );

        /*
         * When the normal center-based check is already close enough, leave
         * the attack entirely to the existing AI so this helper cannot double
         * the normal melee attack rate.
         */
        double centerDistanceSq = attacker.getDistanceSq(
                mumakil.posX,
                mumakil.boundingBox.minY,
                mumakil.posZ
        );

        if (centerDistanceSq <= reachSq) {
            return;
        }

        if (getBoundingBoxDistanceSq(
                attacker.boundingBox,
                mumakil.boundingBox
        ) > reachSq) {
            return;
        }

        /*
         * Retain a line-of-sight requirement so the enlarged effective melee
         * surface does not permit attacks through solid walls.
         */
        if (!attacker.getEntitySenses().canSee(mumakil)) {
            return;
        }

        long worldTime =
                attacker.worldObj.getTotalWorldTime();
        NBTTagCompound data = attacker.getEntityData();

        if (data.getLong(NEXT_EDGE_MELEE_TICK_KEY)
                > worldTime) {
            return;
        }

        if (attacker.attackEntityAsMob(mumakil)) {
            data.setLong(
                    NEXT_EDGE_MELEE_TICK_KEY,
                    worldTime + EDGE_MELEE_COOLDOWN_TICKS
            );
        }
    }

    private static double getNormalMeleeReachSq(
            EntityCreature attacker,
            EntityLivingBase target
    ) {
        double attackerDiameter =
                (double)attacker.width * 2.0D;

        return attackerDiameter * attackerDiameter
                + (double)target.width;
    }

    private static double getBoundingBoxDistanceSq(
            AxisAlignedBB first,
            AxisAlignedBB second
    ) {
        double dx = axisGap(
                first.minX,
                first.maxX,
                second.minX,
                second.maxX
        );
        double dy = axisGap(
                first.minY,
                first.maxY,
                second.minY,
                second.maxY
        );
        double dz = axisGap(
                first.minZ,
                first.maxZ,
                second.minZ,
                second.maxZ
        );

        return dx * dx + dy * dy + dz * dz;
    }

    private static double axisGap(
            double firstMin,
            double firstMax,
            double secondMin,
            double secondMax
    ) {
        if (firstMax < secondMin) {
            return secondMin - firstMax;
        }

        if (secondMax < firstMin) {
            return firstMin - secondMax;
        }

        return 0.0D;
    }
}