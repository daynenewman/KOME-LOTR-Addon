package com.enovak.lotrmoremobs.handler;

import com.enovak.lotrmoremobs.util.MumakilFormationCreditHelper;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import java.lang.reflect.Field;
import java.util.UUID;
import lotr.common.LOTRMod;
import lotr.common.entity.LOTREntityInvasionSpawner;
import lotr.common.entity.npc.LOTREntityNPC;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.living.LivingDeathEvent;

/**
 * Bridges custom formation damage into LOTR's native hired-unit death path.
 *
 * The event source is attributed to the already-hired driver where possible,
 * so LOTR remains authoritative for alignment, pledge, conquest zones, faction
 * relations, and multipliers. No conquest values are modified here.
 */
public final class MumakilFormationCreditEventHandler {
    public static final String INVASION_CREDIT_APPLIED_KEY =
            "lotrmoremobs_formationInvasionCreditApplied";
    public static final String CREDIT_SOURCE_REWRITTEN_KEY =
            "lotrmoremobs_formationCreditSourceRewritten";
    private static final String HIRED_KILL_STAT_RECORDED_KEY =
            "lotrmoremobs_formationHiredKillStatRecorded";
    private static final boolean DEBUG_CREDIT = false;
    private static final Field LIVING_DEATH_SOURCE_FIELD =
            resolveLivingDeathSourceField();

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onLivingDeath(LivingDeathEvent event) {
        if (event == null
                || event.entityLiving == null
                || event.entityLiving.worldObj == null
                || event.entityLiving.worldObj.isRemote
                || LOTRMod.getDamagingPlayerIncludingUnits(event.source)
                != null) {
            return;
        }

        MumakilFormationCreditHelper.Credit credit =
                MumakilFormationCreditHelper.resolve(event.source);
        if (credit == null) {
            return;
        }

        DamageSource nativeSource = getNativeAttributionSource(credit);
        if (nativeSource == null
                || LIVING_DEATH_SOURCE_FIELD == null
                || !setEventSource(event, nativeSource)) {
            return;
        }
        event.entityLiving.getEntityData().setBoolean(
                CREDIT_SOURCE_REWRITTEN_KEY,
                true
        );

        /*
         * LOTREntityNPC.onDeath retains its original method parameter after
         * Forge returns, so its later invasion hook cannot see the event-only
         * source attribution. Apply that one native decrement here.
         */
        applyNativeInvasionKillOnce(event, credit.owner);

        if (DEBUG_CREDIT) {
            System.out.println(
                    "[LOTRMoreMobs] Credited formation kill to "
                            + credit.owner.getCommandSenderName()
            );
        }
    }

    private static DamageSource getNativeAttributionSource(
            MumakilFormationCreditHelper.Credit credit
    ) {
        Entity rider = credit.mumakil.riddenByEntity;
        if (rider instanceof LOTREntityNPC) {
            LOTREntityNPC driver = (LOTREntityNPC)rider;
            UUID driverOwner =
                    driver.hiredNPCInfo.getHiringPlayerUUID();
            if (driver.isEntityAlive()
                    && driver.hiredNPCInfo.isActive
                    && driverOwner != null
                    && driverOwner.equals(credit.owner.getUniqueID())) {
                return DamageSource.causeMobDamage(driver);
            }
        }

        /*
         * A normal hired Southron contributes only while a live, active
         * hired NPC is the damage source. Falling back to player damage here
         * invoked LOTR's direct-player alignment/stat path, which is broader
         * than native hired-unit credit.
         */
        return null;
    }

    /**
     * The source rewrite lets LOTR's global death handler see an ordinary
     * hired Southron source. The victim's vanilla onDeath method still holds
     * the original DamageSource, so it cannot call the driver's normal
     * hired-unit kill counter/XP hook. Apply precisely that hired-info hook
     * once after normal-priority death handlers have accepted the kill.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onLivingDeathRecordHiredStat(LivingDeathEvent event) {
        if (event == null
                || event.entityLiving == null
                || event.entityLiving.worldObj == null
                || event.entityLiving.worldObj.isRemote) {
            return;
        }

        NBTTagCompound data = event.entityLiving.getEntityData();
        if (!data.getBoolean(CREDIT_SOURCE_REWRITTEN_KEY)
                || data.getBoolean(HIRED_KILL_STAT_RECORDED_KEY)
                || !(event.source.getEntity() instanceof LOTREntityNPC)) {
            return;
        }

        LOTREntityNPC driver =
                (LOTREntityNPC)event.source.getEntity();
        if (!driver.isEntityAlive()
                || !driver.hiredNPCInfo.isActive
                || driver.hiredNPCInfo.getHiringPlayer() == null) {
            return;
        }

        data.setBoolean(HIRED_KILL_STAT_RECORDED_KEY, true);
        driver.hiredNPCInfo.onKillEntity(event.entityLiving);
    }

    private static void applyNativeInvasionKillOnce(
            LivingDeathEvent event,
            EntityPlayer owner
    ) {
        if (!(event.entityLiving instanceof LOTREntityNPC)) {
            return;
        }

        LOTREntityNPC victim = (LOTREntityNPC)event.entityLiving;
        if (!victim.isInvasionSpawned()) {
            return;
        }

        NBTTagCompound data = victim.getEntityData();
        if (data.getBoolean(INVASION_CREDIT_APPLIED_KEY)) {
            return;
        }

        LOTREntityInvasionSpawner invasion =
                LOTREntityInvasionSpawner.locateInvasionNearby(
                        victim,
                        victim.getInvasionID()
                );
        if (invasion != null) {
            data.setBoolean(INVASION_CREDIT_APPLIED_KEY, true);
            invasion.addPlayerKill(owner);
        }
    }

    private static Field resolveLivingDeathSourceField() {
        try {
            Field field =
                    LivingDeathEvent.class.getDeclaredField("source");
            field.setAccessible(true);
            return field;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static boolean setEventSource(
            LivingDeathEvent event,
            DamageSource source
    ) {
        try {
            LIVING_DEATH_SOURCE_FIELD.set(event, source);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }
}
