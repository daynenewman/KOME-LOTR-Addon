package com.enovak.lotrmoremobs.handler;

import com.enovak.lotrmoremobs.entity.animal.LOTREntityMumakil;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import java.util.List;
import lotr.common.LOTRMod;
import lotr.common.entity.npc.LOTREntityNPC;
import lotr.common.fac.LOTRFaction;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.event.entity.living.LivingEvent;

/**
 * Makes a hired-war Mumakil a visible Near Harad combat target for hostile
 * LOTR NPC factions without changing the Mumakil's own target or navigator.
 *
 * HIRED_MUMAKIL_NEAR_HARAD_ENEMY_SIGHT_V1
 */
public final class MumakilEnemySightEventHandler {
    private static final String NEXT_SCAN_KEY =
            "LOTRMoreMobsHiredMumakilEnemySightNextScan";

    /*
     * One scan every 20-30 ticks per hired Mumakil. The random jitter prevents
     * multiple Mumakil from repeatedly scanning on the same server tick.
     */
    private static final int SCAN_INTERVAL_TICKS = 20;
    private static final int SCAN_JITTER_TICKS = 11;

    private static final double SIGHT_RANGE = 36.0D;
    private static final double VERTICAL_RANGE = 24.0D;

    @SubscribeEvent
    public void onLivingUpdate(LivingEvent.LivingUpdateEvent event) {
        if (event == null
                || event.entityLiving == null
                || event.entityLiving.worldObj == null
                || event.entityLiving.worldObj.isRemote
                || !(event.entityLiving instanceof LOTREntityMumakil)) {
            return;
        }

        LOTREntityMumakil mumakil =
                (LOTREntityMumakil)event.entityLiving;

        /*
         * HIRED_WAR persists after the driver dies, so the Mumakil continues
         * to be treated as a Near Harad war entity in that state.
         */
        if (!mumakil.isHiredWarMumakil()
                || mumakil.isDead
                || !mumakil.isEntityAlive()) {
            return;
        }

        long worldTime = mumakil.worldObj.getTotalWorldTime();
        NBTTagCompound data = mumakil.getEntityData();
        long nextScan = data.getLong(NEXT_SCAN_KEY);

        if (nextScan > worldTime) {
            return;
        }

        data.setLong(
                NEXT_SCAN_KEY,
                worldTime
                        + SCAN_INTERVAL_TICKS
                        + mumakil.worldObj.rand.nextInt(
                        SCAN_JITTER_TICKS
                )
        );

        List nearbyNPCs = mumakil.worldObj.getEntitiesWithinAABB(
                LOTREntityNPC.class,
                mumakil.boundingBox.expand(
                        SIGHT_RANGE,
                        VERTICAL_RANGE,
                        SIGHT_RANGE
                )
        );

        for (int i = 0; i < nearbyNPCs.size(); ++i) {
            Object object = nearbyNPCs.get(i);

            if (!(object instanceof LOTREntityNPC)) {
                continue;
            }

            LOTREntityNPC enemy = (LOTREntityNPC)object;

            if (canAcquireHiredMumakil(enemy, mumakil)) {
                enemy.setAttackTarget(mumakil);
            }
        }
    }

    private boolean canAcquireHiredMumakil(
            LOTREntityNPC enemy,
            LOTREntityMumakil mumakil
    ) {
        if (enemy == null
                || enemy == mumakil.riddenByEntity
                || enemy.isDead
                || !enemy.isEntityAlive()) {
            return false;
        }

        /*
         * Do not steal an NPC away from a living combat target. This adds the
         * Mumakil as an on-sight target without repeatedly rewriting normal
         * LOTR combat decisions.
         */
        EntityLivingBase currentTarget = enemy.getAttackTarget();
        if (currentTarget != null && currentTarget.isEntityAlive()) {
            return false;
        }

        LOTRFaction enemyFaction = LOTRMod.getNPCFaction(enemy);
        if (enemyFaction == null
                || !LOTRFaction.NEAR_HARAD.isBadRelation(
                enemyFaction
        )) {
            return false;
        }

        /*
         * Preserve normal ownership/attack permissions for active hired NPCs.
         * Unhired faction NPCs are governed by the faction-relation check.
         */
        if (enemy.hiredNPCInfo != null
                && enemy.hiredNPCInfo.isActive
                && !LOTRMod.canNPCAttackEntity(
                enemy,
                mumakil,
                false
        )) {
            return false;
        }

        /*
         * "On sight" means the hostile NPC must have a real line of sight to
         * the Mumakil rather than detecting it through solid terrain.
         */
        return enemy.getEntitySenses().canSee(mumakil);
    }
}